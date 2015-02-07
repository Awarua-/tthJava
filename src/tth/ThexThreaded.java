package tth;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Java Tiger Tree Hash Threaded
 *
 * Based on the C# version by Gil Schmidt, Gil_Smdt@hotmali.com
 * http://www.codeproject.com/Articles/9336/ThexCS-TTH-tiger-tree-hash-maker-in-C
 *
 * This implementation has a few efficiency improvements and is only limited by the read speed of the disk
 * that the file is being hashed from.
 *
 * By default the number of threads is 4, increasing it beyond this didn't seem to have any improvements as the
 * bottleneck becomes the disk read speed.
 * Although it has not been tested if more threads when using a disk with a read speed above 500mb/s has an
 * effect on performance.
 *
 * The program  takes two arguments,
 * File to hash.
 * Number of threads (optional), default 4.
 *
 * @author Dion Woolley, woolley.dion@gmail.com
 */


public class ThexThreaded {
    final byte LeafHash = 0x00;
    final byte InternalHash = 0x01;
    final int LeafSize = 1024; // Do not change this value
    final int DataBlockSize = LeafSize * 1024; // 1 MB
    int ThreadCount = 4;
    final int ZERO_BYTE_FILE = 0;

    public byte[][][] TTH;
    public int LevelCount;
    String Filename;
    int LeafCount;
    FileInputStream FilePtr;

    FileBlock[] FileParts = new FileBlock[ThreadCount];
    Thread[] ThreadsList = new Thread[ThreadCount];

    public byte[] GetTTH_Value(String Filename) throws IOException {
        GetTTH(Filename);
        return TTH[LevelCount - 1][0];
    }

    private void GetTTH(String Filename) throws IOException {
        this.Filename = Filename;

        try {
            OpenFile();

            if (Initialize()) {
                SplitFile();
                StartThreads();
                CompressTree();
            }
        } catch (Exception e) {
            System.out.println("error while trying to get TTH: " + e.getMessage());
            StopThreads();
        }

        if (FilePtr != null) FilePtr.close();
    }

    void OpenFile() throws FileNotFoundException {
        FilePtr = new FileInputStream(Filename); //,DataBlockSize);
    }

    boolean Initialize() throws IOException {
        if (FilePtr.getChannel().size() == ZERO_BYTE_FILE) {
            Tiger TG = new Tiger();

            LevelCount = 1;

            TTH = new byte[1][][];
            TTH[0] = new byte[1][];

            TTH[0][0] = TG.ComputeHash(new byte[1]);

            return false;
        }
        else {
            int i = 1;
            LevelCount = 1;

            LeafCount = (int) (FilePtr.getChannel().size() / LeafSize);
            if ((FilePtr.getChannel().size() % LeafSize) > 0) LeafCount++;

            while (i < LeafCount) {
                i *= 2;
                LevelCount++;
            }

            TTH = new byte[LevelCount][][];
            TTH[0] = new byte[LeafCount][];
        }

        return true;
    }

    void SplitFile() throws IOException {
        long LeafsInPart = LeafCount / ThreadCount;

        // check if file is bigger then 1 MB or don't use threads
        if (FilePtr.getChannel().size() > 1024 * 1024)
            for (int i = 0; i < ThreadCount; i++)
                FileParts[i] = new FileBlock(LeafsInPart * LeafSize * i,
                        LeafsInPart * LeafSize * (i + 1));

        FileParts[ThreadCount - 1].End = FilePtr.getChannel().size();
    }

    void StartThreads() throws InterruptedException {
        boolean ThreadsAreWorking;
        for (int i = 0; i < ThreadCount; i++) {
            ThreadsList[i] = new Thread(() -> {
                try {
                    ProcessLeafs();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            ThreadsList[i].setName(String.valueOf(i));
            ThreadsList[i].start();
        }


        do {
            Thread.sleep(1000);
            ThreadsAreWorking = false;

            for (int i = 0; i < ThreadCount; i++)
                if (ThreadsList[i].isAlive())
                    ThreadsAreWorking = true;


        } while (ThreadsAreWorking);
    }

    void StopThreads() {
        for (int i = 0; i < ThreadCount; i++)
            if (ThreadsList[i] != null && ThreadsList[i].isAlive())
                ThreadsList[i].interrupt();
    }

    private static void BlockCopy(byte[]  src, int srcOffset, byte[] dst, int dstOffset, int count) {
        for (int i = 0; i < count; i++) {
            dst[dstOffset + i] = src[srcOffset + i];
        }
    }

    void ProcessLeafs() throws IOException {
        FileInputStream ThreadFilePtr = new FileInputStream(Filename);
        FileBlock ThreadFileBlock = FileParts[Short.valueOf(Thread.currentThread().getName())];
        Tiger TG = new Tiger();
        byte[] DataBlock;
        byte[] Data = new byte[LeafSize + 1];
        long LeafIndex;
        int BlockLeafs;
        int i;

        ThreadFilePtr.getChannel().position(ThreadFileBlock.Start);

        while (ThreadFilePtr.getChannel().position() < ThreadFileBlock.End) {
            LeafIndex = ThreadFilePtr.getChannel().position() / 1024;

            if (ThreadFileBlock.End - ThreadFilePtr.getChannel().position() < DataBlockSize)
                DataBlock = new byte[(int) (ThreadFileBlock.End - ThreadFilePtr.getChannel().position())];
            else
                DataBlock = new byte[DataBlockSize];

            ThreadFilePtr.read(DataBlock, 0, DataBlock.length); //read block

            BlockLeafs = DataBlock.length / 1024;

            for (i = 0; i < BlockLeafs; i++) {
                BlockCopy(DataBlock, i * LeafSize, Data, 1, LeafSize);

                TG.Initialize();
                TTH[0][((int) LeafIndex++)] = TG.ComputeHash(Data);
            }

            if (i * LeafSize < DataBlock.length) {
                Data = new byte[DataBlock.length - BlockLeafs * LeafSize + 1];
                Data[0] = LeafHash;

                BlockCopy(DataBlock, BlockLeafs * LeafSize, Data, 1, (Data.length - 1));

                TG.Initialize();
                TTH[0][((int) LeafIndex++)] = TG.ComputeHash(Data);

                Data = new byte[LeafSize + 1];
                Data[0] = LeafHash;
            }
        }
    }

    void CompressTree() {
        int InternalLeafCount;
        int Level = 0, i, LeafIndex;

        while (Level + 1 < LevelCount) {
            LeafIndex = 0;
            InternalLeafCount = (LeafCount / 2) + (LeafCount % 2);
            TTH[Level + 1] = new byte[InternalLeafCount][];

            for (i = 1; i < LeafCount; i += 2)
                ProcessInternalLeaf(Level + 1, LeafIndex++, TTH[Level][i - 1], TTH[Level][i]);

            if (LeafIndex < InternalLeafCount)
                TTH[Level + 1][LeafIndex] = TTH[Level][LeafCount - 1];

            Level++;
            LeafCount = InternalLeafCount;
        }
    }

    void ProcessInternalLeaf(int Level, int Index, byte[] LeafA, byte[] LeafB) {
        Tiger TG = new Tiger();
        byte[] Data = new byte[LeafA.length + LeafB.length + 1];

        Data[0] = InternalHash;

        BlockCopy(LeafA, 0, Data, 1, LeafA.length);
        BlockCopy(LeafB, 0, Data, LeafA.length + 1, LeafA.length);

        TG.Initialize();
        TTH[Level][Index] = TG.ComputeHash(Data);
    }

    class FileBlock {
        public long Start, End;

        public FileBlock(long Start, long End) {
            this.Start = Start;
            this.End = End;
        }
    }

    public static void main(String[] args) {
        ThexThreaded thex = new ThexThreaded();
        byte[] result;
        Instant start;
        Instant end;
        if (!(args.length < 1) && !args[0].isEmpty()) {
            if (args.length > 1 && args[1].matches("[0-9]")) {
                thex.ThreadCount = Integer.valueOf(args[1]);
            }
            File file = new File(args[0]);
            if (file.exists()) {
                try {
                    System.out.println("Running with " + String.valueOf(thex.ThreadCount) + " threads");
                    System.out.println("Start hashing file: " + file.getName());

                    start = Instant.now();
                    result = thex.GetTTH_Value(args[0]);
                    end = Instant.now();

                    System.out.println("Finished hashing file: " + file.getName());
                    System.out.println("THH: " + Base32.encode(result));
                    System.out.println("TimeTaken: " + Duration.between(start, end));

                } catch (IOException e) {
                    System.err.println("Something went wrong trying to hash file: " + file.getName());
                    e.printStackTrace();
                }
            } else {
                System.out.println("The given file does not exist");
            }
        }
        else {
            System.out.println("No file given");
        }
    }
}


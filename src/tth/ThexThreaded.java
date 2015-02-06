package tth;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Created by Dion on 2/6/2015.
 */
/*
 *
 * Tiger Tree Hash Threaded - by Gil Schmidt.
 *
 *  - this code was writtin based on:
 *    "Tree Hash EXchange format (THEX)"
 *    http://www.open-content.net/specs/draft-jchapweske-thex-02.html
 *
 *  - the tiger hash class was converted from visual basic code called TigerNet:
 *    http://www.hotpixel.net/software.html
 *
 *  - Base32 class was taken from:
 *    http://msdn.microsoft.com/msdnmag/issues/04/07/CustomPreferences/default.aspx
 *    didn't want to waste my time on writing a Base32 class.
 *
 *  - along with the request for a version the return the full TTH tree and the need
 *    for a faster version i rewrote a thread base version of the ThexCS.
 *    i must say that the outcome wasn't as good as i thought it would be.
 *    after writing ThexOptimized i noticed that the major "speed barrier"
 *    was reading the data from the file so i decide to split it into threads
 *    that each one will read the data and will make the computing process shorter.
 *    in testing i found out that small files (about 50 mb) are being processed
 *    faster but in big files (700 mb) it was slower, also the CPU is working better
 *    with more threads.
 *
 *  - the update for the ThexThreaded is now including a Dispose() function to free
 *    some memory which is mostly taken by the TTH array, also i changed the way the
 *    data is pulled out of the file so it would read data block instead of data leaf
 *    every time (reduced the i/o reads dramaticly and go easy on the hd) i used 1 MB
 *    blocks for each thread you can set it at DataBlockSize (put something like:
 *    LeafSize * N). the method for copying bytes is change to Buffer.BlockCopy it's
 *    faster but you won't notice it too much.
 *
 *    (a lot of threads = slower but the cpu is working less, i recommend 3-5 threads)
 *
 * - fixed code for 0 byte file (thanks Flow84).
 *
 *
 *  if you use this code please add my name and email to the references!
 *  [ contact me at Gil_Smdt@hotmali.com ]
 */


public class ThexThreaded {
    final byte LeafHash = 0x00;
    final byte InternalHash = 0x01;
    final int LeafSize = 1024;
    final int DataBlockSize = LeafSize * 1024; // 1 MB
    final int ThreadCount = 4;
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

    public byte[][][] GetTTH_Tree(String Filename) throws IOException {
        GetTTH(Filename);
        return TTH;
    }

    private void GetTTH(String Filename) throws IOException {
        this.Filename = Filename;

        try {
            OpenFile();

            if (Initialize()) {
                SplitFile();
                System.out.println("starting to get TTH: " + new Date().toString());
                StartThreads();
                System.out.println("finished to get TTH: " + new Date().toString());
                CompressTree();
            }
        } catch (Exception e) {
            System.out.println("error while trying to get TTH: " + e.getMessage());
            StopThreads();
        }

        if (FilePtr != null) FilePtr.close();
    }

    void Dispose() {
        TTH = null;
        ThreadsList = null;
        FileParts = null;
    }

    void OpenFile() throws Exception {
        if (!new File(Filename).exists())
            throw new Exception("file doesn't exists!");

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

        boolean ThreadsAreWorking = false;

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

        DataBlock = null;
        Data = null;
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
        ThexThreaded test = new ThexThreaded();
        byte[] result;
        Instant start;
        Instant end;
        try {
            start = Instant.now();


            result = test.GetTTH_Value(args[0]);
            end = Instant.now();

            System.out.println("THH: " + Base32.encode(result));
            System.out.println("TimeTaken: " + Duration.between(start, end));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}


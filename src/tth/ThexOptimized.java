package tth;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Java Tiger Tree Hash Optimized
 *
 * Based on the C# version by Gil Schmidt, Gil_Smdt@hotmali.com
 * http://www.codeproject.com/Articles/9336/ThexCS-TTH-tiger-tree-hash-maker-in-C
 *
 * This implementation has a few efficiency improvements
 *
 * The program  takes one argument,
 * File to hash.
 *
 * @author Dion Woolley, woolley.dion@gmail.com
 */

public class ThexOptimized {

    final   int        ZERO_BYTE_FILE  = 0;    //file with no data.
    final   int        Block_Size      = 64;   //64k
    final   int        Leaf_Size       = 1024; //1k - don't change this.

    private int        Leaf_Count; //number of leafs.
    private byte[][]   HashValues; //array for hash values.
    private FileInputStream FilePtr;    //dest file stream pointer.

    public byte[] GetTTH(String Filename) throws IOException {
        byte[] TTH;

        try
        {
            if (!new File(Filename).exists())
            {
                System.err.println("file doesn't exists " + Filename);
                return null;
            }

            //open the file.
            FilePtr = new FileInputStream(Filename);

            //if the file is 0 byte long.
            if (FilePtr.getChannel().size() == ZERO_BYTE_FILE)
            {
                Tiger TG = new Tiger();

                TTH = TG.ComputeHash(new byte[] { 0 });
            }

            //the file is smaller then a single leaf.
            else if (FilePtr.getChannel().size() <= Leaf_Size * Block_Size)
                TTH = CompressSmallBlock();

                //normal file.
            else
            {
                Leaf_Count = (int)(FilePtr.getChannel().size() / Leaf_Size);
                if (FilePtr.getChannel().size() % Leaf_Size > 0) Leaf_Count++;

                GetLeafHash(); //get leafs hash from file.
                TTH = CompressHashBlock(HashValues, Leaf_Count);	//get root TTH from hash array.
            }
        }
        catch (Exception e)
        {
            System.err.println("error while trying to get TTH for file: " +
                    Filename + ". (" + e.getMessage() + ")");

            TTH = null;
        }

        if (FilePtr != null) FilePtr.close();

        return TTH;
    }

    private void GetLeafHash() throws IOException {
        //gets the leafs from the file and hashes them.
        int i;
        int Blocks_Count = Leaf_Count / (Block_Size * 2);

        if (Leaf_Count % (Block_Size * 2) > 0) Blocks_Count++;
        HashValues = new byte[Blocks_Count][];

        byte[][] HashBlock = new byte[Block_Size][];

        for (i = 0; i < Leaf_Count / (Block_Size * 2); i++) //loops threw the blocks.
        {
            for (int LeafIndex = 0; LeafIndex < Block_Size; LeafIndex++) //creates new block.
                HashBlock[LeafIndex] = GetNextLeafHash(); //extracts two leafs to a hash.

            HashValues[i] = CompressHashBlock(HashBlock,Block_Size); //compresses the block to hash.
        }

        if (i < Blocks_Count) HashValues[i] = CompressSmallBlock(); //this block wasn't big enough.

        Leaf_Count = Blocks_Count;
    }

    private byte[] GetNextLeafHash() throws IOException {
        //reads 2 leafs from the file and returns their combined hash.
        byte[] LeafA = new byte[Leaf_Size];
        byte[] LeafB = new byte[Leaf_Size];


        int DataSize = FilePtr.read(LeafA, 0, Leaf_Size);
        //check if leaf is too small.
        if (DataSize < Leaf_Size || FilePtr.getChannel().position() == FilePtr.getChannel().size()) {
            return (LeafHash(ByteExtract(LeafA, DataSize)));
        }

        DataSize = FilePtr.read(LeafB,0,Leaf_Size);

        if (DataSize < Leaf_Size)
            LeafB = ByteExtract(LeafB,DataSize);

        LeafA = LeafHash(LeafA);
        LeafB = LeafHash(LeafB);

        return InternalHash(LeafA,LeafB); //returns combined hash.
    }

    private byte[] CompressHashBlock(byte[][] HashBlock,int HashCount) {
        if (HashBlock.length == 0) return null;

        while (HashCount > 1) //until there's only 1 hash.
        {
            int TempBlockSize = HashCount / 2;
            if (HashCount % 2 > 0) TempBlockSize++;

            byte[][] TempBlock = new byte[TempBlockSize][];

            int HashIndex = 0;
            for (int i = 0; i < HashCount / 2; i++) //makes hash from pairs.
            {
                TempBlock[i] = InternalHash(HashBlock[HashIndex],HashBlock[HashIndex+1]);
                HashIndex += 2;
            }

            //this one doesn't have a pair :(
            if (HashCount % 2 > 0) TempBlock[TempBlockSize - 1] = HashBlock[HashCount - 1];

            HashBlock = TempBlock;
            HashCount = TempBlockSize;
        }
        return HashBlock[0];
    }

    private byte[] CompressSmallBlock() throws IOException {
        long DataSize = FilePtr.getChannel().size() - FilePtr.getChannel().position();

        int LeafCount = (int) DataSize / (Leaf_Size * 2);
        if (DataSize % (Leaf_Size * 2) > 0) LeafCount++;

        byte[][] SmallBlock = new byte[LeafCount][];

        //extracts leafs from file.
        for (int i = 0; i < (int) DataSize / (Leaf_Size * 2); i++)
            SmallBlock[i] = GetNextLeafHash();

        if (DataSize % (Leaf_Size * 2) > 0) SmallBlock[LeafCount - 1] = GetNextLeafHash();

        return CompressHashBlock(SmallBlock,LeafCount); //gets hash from the small block.
    }

    private byte[] InternalHash(byte[] LeafA,byte[] LeafB) {
        byte[] Data = new byte[LeafA.length + LeafB.length + 1];

        Data[0] = 0x01; //internal hash mark.

        //combines two leafs.
        System.arraycopy(LeafA, 0, Data, 1, LeafA.length);
        System.arraycopy(LeafB, 0, Data, LeafA.length + 1, LeafB.length);

        //gets tiger hash value for combined leaf hash.
        Tiger TG = new Tiger();
        TG.Initialize();
        return TG.ComputeHash(Data);
    }

    private byte[] LeafHash(byte[] Raw_Data) {
        byte[] Data = new byte[Raw_Data.length + 1];

        Data[0] = 0x00; //leaf hash mark.
        System.arraycopy(Raw_Data, 0, Data, 1, Raw_Data.length);

        //gets tiger hash value for leafs blocks.
        Tiger TG = new Tiger();
        TG.Initialize();
        return TG.ComputeHash(Data);
    }

    private byte[] ByteExtract(byte[] Raw_Data,int Data_Length) {
    //if we use the extra 0x00 in Raw_Data we will get wrong hash.
        byte[] Data = new byte[Data_Length];
        System.arraycopy(Raw_Data, 0, Data, 0, Data_Length);

        return Data;
    }

    public static void main(String[] args) {
        ThexOptimized thex = new ThexOptimized();
        byte[] result;
        Instant start;
        Instant end;
        if (!(args.length < 1) && !args[0].isEmpty()) {
            File file = new File(args[0]);
            if (file.exists()) {
                try {
                    System.out.println("Start hashing file: " + file.getName());
                    start = Instant.now();


                    result = thex.GetTTH(args[0]);
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

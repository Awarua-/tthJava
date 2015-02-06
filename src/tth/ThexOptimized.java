package tth;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/**
 * Created by Dion on 2/6/2015.
 */

/*
 *
 * Tiger Tree Hash Optimized - by Gil Schmidt.
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
 *  - after making the first working TTH class i noticed that it was very slow,i decided
 *    to improve the code by fixed size ('Block_Size') blocks and compress them into 1 hash
 *    string. (save memory and makes the cpu works a little less in a smaller arrays).
 *
 *  - increase 'Block_Size' for better performance but it will cost you by memory.
 *
 *  - fixed code for 0 byte file (thanks Flow84).
 *
 *  if you use this code please add my name and email to the references!
 *  [ contact me at Gil_Smdt@hotmali.com ]
 *
 */

public class ThexOptimized
{
    final   int        ZERO_BYTE_FILE  = 0;    //file with no data.
    final   int        Block_Size      = 64;   //64k
    final   int        Leaf_Size       = 1024; //1k - don't change this.

    private int        Leaf_Count; //number of leafs.
    private byte[][]   HashValues; //array for hash values.
    private FileInputStream FilePtr;    //dest file stream pointer.

    public byte[] GetTTH(String Filename) throws IOException {
        byte[] TTH = null;

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
                System.err.println("===> [ Moving to internal hash. ]");
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

    private void GetLeafHash() throws IOException //gets the leafs from the file and hashes them.
    {
        int i;
        int Blocks_Count = (int) Leaf_Count / (Block_Size * 2);

        if (Leaf_Count % (Block_Size * 2) > 0) Blocks_Count++;
        HashValues = new byte[Blocks_Count][];

        byte[][] HashBlock = new byte[(int)Block_Size][];

        for (i = 0; i < (int) Leaf_Count / (Block_Size * 2); i++) //loops threw the blocks.
        {
            for (int LeafIndex = 0; LeafIndex < (int) Block_Size; LeafIndex++) //creates new block.
                HashBlock[LeafIndex] = GetNextLeafHash(); //extracts two leafs to a hash.

            HashValues[i] = CompressHashBlock(HashBlock,Block_Size); //compresses the block to hash.
        }

        if (i < Blocks_Count) HashValues[i] = CompressSmallBlock(); //this block wasn't big enough.

        Leaf_Count = Blocks_Count;
    }

    private byte[] GetNextLeafHash() throws IOException //reads 2 leafs from the file and returns their combined hash.
    {
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

    private byte[] CompressHashBlock(byte[][] HashBlock,int HashCount) //compresses hash blocks to hash.
    {
        if (HashBlock.length == 0) return null;

        while (HashCount > 1) //until there's only 1 hash.
        {
            int TempBlockSize = (int) HashCount / 2;
            if (HashCount % 2 > 0) TempBlockSize++;

            byte[][] TempBlock = new byte[TempBlockSize][];

            int HashIndex = 0;
            for (int i = 0; i < (int) HashCount / 2; i++) //makes hash from pairs.
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

    private byte[] CompressSmallBlock() throws IOException //compress a small block to hash.
    {
        long DataSize = (long) (FilePtr.getChannel().size() - FilePtr.getChannel().position());

        int LeafCount = (int) DataSize / (Leaf_Size * 2);
        if (DataSize % (Leaf_Size * 2) > 0) LeafCount++;

        byte[][] SmallBlock = new byte[LeafCount][];

        //extracts leafs from file.
        for (int i = 0; i < (int) DataSize / (Leaf_Size * 2); i++)
            SmallBlock[i] = GetNextLeafHash();

        if (DataSize % (Leaf_Size * 2) > 0) SmallBlock[LeafCount - 1] = GetNextLeafHash();

        return CompressHashBlock(SmallBlock,LeafCount); //gets hash from the small block.
    }
    private byte[] InternalHash(byte[] LeafA,byte[] LeafB) //internal hash.
    {
        byte[] Data = new byte[LeafA.length + LeafB.length + 1];

        Data[0] = 0x01; //internal hash mark.

        //combines two leafs.
        for (int i = 0; i < LeafA.length; i++) {
            Data[i + 1] = LeafA[i];
        }
        for (int i = 0; i < LeafB.length; i++) {
            Data[LeafA.length + 1 + i] = LeafB[i];
        }

        //gets tiger hash value for combined leaf hash.
        Tiger TG = new Tiger();
        TG.Initialize();
        return TG.ComputeHash(Data);
    }

    private byte[] LeafHash(byte[] Raw_Data) //leaf hash.
    {
        byte[] Data = new byte[Raw_Data.length + 1];

        Data[0] = 0x00; //leaf hash mark.
        for (int i = 0; i < Raw_Data.length; i++) {
            Data[i + 1] = Raw_Data[i];
        }

        //gets tiger hash value for leafs blocks.
        Tiger TG = new Tiger();
        TG.Initialize();
        return TG.ComputeHash(Data);
    }

    private byte[] ByteExtract(byte[] Raw_Data,int Data_Length) //if we use the extra 0x00 in Raw_Data we will get wrong hash.
    {
        byte[] Data = new byte[Data_Length];

        for (int i = 0; i < Data_Length; i++)
            Data[i] = Raw_Data[i];

        return Data;
    }

    public static void main(String[] args) {
        ThexOptimized test = new ThexOptimized();
        byte[] result;
        Instant start;
        Instant end;
        try {
            start = Instant.now();


            result = test.GetTTH("D:\\Documents\\47 Ronin 2013 1080p.mkv");
            end = Instant.now();

            System.out.println(Base32.encode(result));
            System.out.println(Duration.between(start, end));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

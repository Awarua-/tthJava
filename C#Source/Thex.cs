/*
 * 
 * Tiger Tree Hash - by Gil Schmidt.
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
 *  - fixed code for 0 byte file (thanks Flow84).
 * 
 *  if you use this code please add my name and email to the references!
 *  [ contact me at Gil_Smdt@hotmali.com ]
 * 
 */

using System;
using System.Text;
using System.IO;
using System.Collections;

namespace TTH
{
	public class Thex
	{
		const   int        Block_Size = 1024;
        const   int        ZERO_BYTE_FILE = 0;
		private int        Leaf_Count;
		private ArrayList  LeafCollection;
		private FileStream FilePtr;

		private struct HashHolder
		{
			public byte[] HashValue;

			public HashHolder(byte[] HashValue)
			{
				this.HashValue = HashValue;
			}
		}

		public byte[] GetTTH(string Filename) 
		{
			HashHolder Result;

			try
			{
				FilePtr = new FileStream(Filename,FileMode.Open,FileAccess.Read,FileShare.ReadWrite);
			
				//if the file is 0 byte long.
                if (FilePtr.Length == ZERO_BYTE_FILE)
                {
                    Tiger TG = new Tiger();

                    Result.HashValue = TG.ComputeHash(new byte[] { 0 });
                }
                //if there's only one block in file use SmallFile().
                else if (FilePtr.Length <= Block_Size)
                    Result.HashValue = SmallFile();

                else
                {
                    //get how many leafs are in file.
                    Leaf_Count = (int)(FilePtr.Length / Leaf_Size);

                    if (FilePtr.Length % Block_Size > 0)
                        Leaf_Count++;

                    //load blocks of data and get tiger hash for each one.
                    LoadLeafHash();

                    //get root hash from blocks hash.
                    Result = GetRootHash();
                }

                FilePtr.Close();

				return Result.HashValue;
			}
			catch (Exception e)
			{
				System.Diagnostics.Debug.WriteLine("error while trying to get TTH for file: " + 
					Filename + ". (" + e.Message + ")");

                if (FilePtr != null)
                    FilePtr.Close();

				return null;
			}
		}

		private byte[] SmallFile()
		{
			Tiger TG = new Tiger();
			byte[] Block = new byte[Block_Size];

			int BlockSize = FilePtr.Read(Block,0,1024);

			//gets hash for a single block file.
			return LH(ByteExtract(Block,BlockSize));
		}

		private void LoadLeafHash()
		{
			LeafCollection = new ArrayList();

			for (int i = 0; i < (int) Leaf_Count / 2; i++)
			{
				byte[] BlockA = new byte[Block_Size],BlockB = new byte[Block_Size];
				
				FilePtr.Read(BlockA,0,1024);
				int DataSize = FilePtr.Read(BlockB,0,1024);

				//check if the block isn't big enough.
				if (DataSize < Block_Size)
					BlockB = ByteExtract(BlockB,DataSize);

				BlockA = LH(BlockA);
				BlockB = LH(BlockB);

				//add combined leaf hash.
				LeafCollection.Add(new HashHolder(IH(BlockA,BlockB)));
			}

			//leaf without a pair.
			if (Leaf_Count % 2 != 0)
			{
				byte[] Block = new byte[Block_Size];
				int DataSize = FilePtr.Read(Block,0,1024);

				if (DataSize < 1024)
					Block = ByteExtract(Block,DataSize);

				LeafCollection.Add(new HashHolder(LH(Block)));
			}
		}

		private HashHolder GetRootHash()
		{
			ArrayList InternalCollection = new ArrayList();

			do
			{
				InternalCollection = new ArrayList(LeafCollection);
				LeafCollection.Clear();

				while (InternalCollection.Count > 1)
				{
					//load next two leafs.
					byte[] HashA = ((HashHolder) InternalCollection[0]).HashValue;
					byte[] HashB = ((HashHolder) InternalCollection[1]).HashValue;
					
					//add their combined hash.
					LeafCollection.Add(new HashHolder(IH(HashA,HashB)));

					//remove the used leafs.
					InternalCollection.RemoveAt(0);
					InternalCollection.RemoveAt(0);
				}

				//if this leaf can't combine add him at the end.
				if (InternalCollection.Count > 0)
					LeafCollection.Add(InternalCollection[0]);
			} while (LeafCollection.Count > 1);

			return (HashHolder) LeafCollection[0];
		}

		private byte[] IH(byte[] LeafA,byte[] LeafB) //internal hash.
		{ 
			byte[] Data = new byte[LeafA.Length + LeafB.Length + 1];

			Data[0] = 0x01; //internal hash mark.

			//combines two leafs.
			LeafA.CopyTo(Data,1);
			LeafB.CopyTo(Data,LeafA.Length + 1);

			//gets tiger hash value for combined leaf hash.
			Tiger TG = new Tiger();
			TG.Initialize();
			return TG.ComputeHash(Data);			
		}

		private byte[] LH(byte[] Raw_Data) //leaf hash.
		{ 
			byte[] Data = new byte[Raw_Data.Length + 1];

			Data[0] = 0x00; //leaf hash mark.
			Raw_Data.CopyTo(Data,1);

			//gets tiger hash value for leafs blocks.
			Tiger TG = new Tiger();
			TG.Initialize();
			return TG.ComputeHash(Data);
		}

		private byte[] ByteExtract(byte[] Raw_Data,int Data_Length) //copy 
		{
			//return Data_Length bytes from Raw_Data.
			byte[] Data = new byte[Data_Length];

			for (int i = 0; i < Data_Length; i++)
				Data[i] = Raw_Data[i];

			return Data;
		}
	}
}

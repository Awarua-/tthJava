using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Diagnostics;
using System.IO;

namespace TTH

{
    class Program
    {
        static void Main(string[] args)
        {
            byte[] Result = new byte[] { 0 };
            ThexThreaded TTH_Threaded = new ThexThreaded();
            ThexOptimized TTH_Optimized = new ThexOptimized();
            Thex TTH = new Thex();
            string path = Path.Combine(Directory.GetCurrentDirectory(), "\\filedatav2.txt");
            string[] lines = File.ReadAllLines(path);
            string myInput;
            myInput = lines[0];
            if (!File.Exists(myInput))
            {
                Console.WriteLine(myInput + '\n' + "Filename or path Invalid or File does not exist!");
                Console.ReadLine();
            }
            else
            {
                try
                {
                    Result = TTH_Optimized.GetTTH(myInput);
                    using (StreamWriter sw = File.AppendText(path))
                    {
                        sw.WriteLine('\n' + Base32.ToBase32String(Result));
                    }
                }
                catch
                {
                    Console.WriteLine("An error occurred trying to generate TTH, using TTH_Optimized, atempting to run TTH");
                    try
                    {
                        Result = TTH.GetTTH(myInput);
                        using (StreamWriter sw = File.AppendText(path))
                        {
                            sw.WriteLine('\n' + Base32.ToBase32String(Result));
                        }
                    }
                    catch
                    {
                        Console.WriteLine("Well this is embarrassing, all TTH algorithms have failed to hash the file");
                    }
                    
                }
                File.CreateText("\\tthfinished.txt").Close();
                Console.ReadLine();
            }
        }
    }
}

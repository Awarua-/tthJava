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
            Stopwatch watch;
            string myInput;
            myInput = "C:\\47 Ronin 2013 1080p.mkv";
            if (!File.Exists(myInput))
            {
                Console.WriteLine(myInput + '\n' + "Filename or path Invalid or File does not exist!");
            }
            else
            {
                try
                {
                    watch = Stopwatch.StartNew();
                    Result = TTH_Threaded.GetTTH_Value(myInput);
                    watch.Stop();
                    Console.WriteLine('\n' + Base32.ToBase32String(Result));
                    Console.WriteLine('\n' + watch.ElapsedMilliseconds.ToString());
                    Console.Read();
                }
                catch { }
            }
        }
    }
}

using System;
using System.IO;

namespace Tail
{
	class MainClass
	{
		public static void Main (string[] args)
		{
			using (BinaryReader reader = new BinaryReader(new FileStream(args[0], 
                     FileMode.Open, FileAccess.Read, FileShare.ReadWrite))) {
				using (BinaryWriter writer = new BinaryWriter(new FileStream(args[1], 
                     FileMode.Create, FileAccess.Write, FileShare.ReadWrite))) {

					Console.WriteLine (args[1]);
					//start at the end of the file
					//long lastMaxOffset = reader.BaseStream.Length;
					//start at the beginning of the file
					long lastMaxOffset = 0;

					//Stream stdout = Console.OpenStandardOutput ();

					while (true) {
						System.Threading.Thread.Sleep (100);

						//if the file size has not changed, idle
						if (reader.BaseStream.Length == lastMaxOffset)
							continue;

						//seek to the last max offset
						reader.BaseStream.Seek (lastMaxOffset, SeekOrigin.Begin);

						//read out of the file until the EOF
						const int bufferSize = 4096;
						byte[] buffer = new byte[bufferSize];
						int count;
						while ((count = reader.Read(buffer, 0, buffer.Length)) != 0) {
							writer.Write (buffer, 0, count);
						}

						//update the last max offset
						lastMaxOffset = reader.BaseStream.Position;
					}
				}
			}
		}
	}
}

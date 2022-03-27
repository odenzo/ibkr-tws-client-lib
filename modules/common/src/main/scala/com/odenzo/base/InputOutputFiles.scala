package com.odenzo.base
import cats.effect.{IO, Resource}

import cats.effect.IO

import java.io.{File, FileInputStream, FileOutputStream}

object InputOutputFiles {

  /** Loads text file returnuing list of line content with EOL delimeters */
  def loadTextFile(filename: String = "/Users/stevef/Desktop/ODENZO-NZ-Ltd-28SEP2019-to-28SEP2020.csv"): IO[List[String]] = {
    import scala.io._
    val acquire          = IO(Source.fromFile(filename)(Codec.UTF8))
    //os.read.lines(filename)
    def close(s: Source) = IO(s.close())

    Resource
      .make(acquire)(close(_))
      .use { src =>
        IO(src.getLines().toList)
      }

  }

  def withInputStream(f: File): Resource[IO, FileInputStream] =
    Resource.make {
      IO(new FileInputStream(f)) // build
    } { inStream =>
      IO(inStream.close()).handleErrorWith(_ => IO.unit) // release
    }

  /** Resource Manaaged (File) OutputStream */
  def withOutputStream(f: File): Resource[IO, FileOutputStream] = {
    Resource.make {
      IO(new FileOutputStream(f))
    } { outStream =>
      IO(outStream.close()).handleErrorWith(_ => IO.unit) // release
    }
  }
}

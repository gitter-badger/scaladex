package ch.epfl.scala.index
package data
package cleanup

import model._

import utest._

object SemanticVersionTest extends TestSuite{
  val tests = this{
    "ordering"-{
      def order(versions: List[String]) = 
        versions.flatMap(v => SemanticVersionParser(v)).sorted(Ordering[SemanticVersion].reverse)

      "small"-{
        val versions =
          "1.1.1" :: 
          "1.0.1-RC2" ::
          "1.0.1-RC1" ::
          "1.0.1-M2" ::
          "1.0.1-M1" ::
          "1.0.1-BLABLA" ::
          "1.0.1" ::
          Nil

        order(versions)
      }
      "large"-{
        val versions =
          "0.10.0-2bc5c07" ::
          "0.10.0-7f595d5" ::
          "0.10.0-561030e" ::
          "0.10.0-f869e69" ::
          "0.10.0-4603631" ::
          "0.10.0-2b99b08" ::
          "0.10.0-bcd7a90" ::
          "0.10.0-53f8a78" ::
          "0.10.0-b158f13" ::
          "0.10.0-2d552f1" ::
          "0.10.0-80028c4" ::
          "0.10.0-337165e" ::
          "0.10.0-00a4316" ::
          "0.10.0-7fe7aaf" ::
          "0.10.0-a310047" ::
          "0.10.0-5f2040e" ::
          "0.10.0-c846eb5" ::
          "0.10.0-a97b5d5" ::
          "0.10.0-d8968b1" ::
          "0.10.0-872dd6f" ::
          "0.10.0-04c38f1" ::
          "0.10.0-f3a0675" ::
          "0.10.0-6220609" ::
          "0.10.0-5616d9c" ::
          "0.10.0-29cc6dc" ::
          "0.10.0-9efecbf" ::
          "0.10.0-c467532" ::
          "0.10.0-555e9ed" ::
          "0.10.0-7b92cb2" ::
          "0.10.0-f9e5770" ::
          "0.10.0-35a8199" ::
          "0.10.0-4ff0fb4" ::
          "0.10.0-c3e2c7b" ::
          "0.10.0-d688bbb" ::
          "0.10.0-aee77fa" ::
          "0.10.0-6c260e6" ::
          "0.10.0-a937b9e" ::
          "0.10.0-58b0a49" ::
          "0.10.0-fc7391a" ::
          "0.10.0-4351dec" ::
          "0.10.0-afc1a30" ::
          "0.10.0-2375a42" ::
          "0.10.0-6a254b6" ::
          "0.10.0-5ff9688" ::
          "0.10.0-8aff5d7" ::
          "0.10.0-RC1"     ::
          "0.10.0-9e56e6d" ::
          "0.10.0-82f0170" ::
          "0.10.0-6c567b2" ::
          "0.10.0-d01d970" ::
          "0.10.0-929193f" ::
          "0.10.0-7b20123" ::
          "0.10.0-b6f3d42" ::
          "0.10.0-fed3cc1" ::
          "0.10.0-f09e86c" ::
          "0.10.0-87fbc6e" ::
          "0.10.0-056e705" ::
          "0.10.0-6aa5837" ::
          "0.10.0-177004b" ::
          "0.10.0-2d2bdde" ::
          "0.10.0-20f4e35" ::
          "0.10.0-ea9d728" ::
          "0.10.0-7a80979" ::
          "0.10.0-a9ab100" ::
          "0.10.0-c61bc38" ::
          "0.10.0-d31ea91" ::
          "0.10.0-ae447e2" ::
          "0.10.0-40b1866" ::
          "0.10.0-f461593" ::
          "0.10.0-ea6f8ca" ::
          "0.10.0-5a1cc8c" ::
          "0.10.0-d22219e" ::
          "0.10.0-a04bda3" ::
          "0.10.0-M1"      ::
          "0.10.0-530b0bf" ::
          "0.10.0-407dec8" ::
          "0.10.0-02a9795" ::
          "0.10.0-f50924f" ::
          "0.10.0-0bda8f8" ::
          "0.10.0-ae7d2dc" ::
          "0.10.0-d624532" ::
          "0.10.0-RC2"     ::
          "0.10.0-caf9e26" ::
          "0.10.0-9d0a037" ::
          "0.10.0-1366034" ::
          "0.10.0-8b60b5b" ::
          "0.10.0-9d08114" ::
          "0.10.0-e449eda" ::
          "0.10.0-00f0f34" ::
          "0.10.0-55b795d" ::
          "0.10.0-df9500b" ::
          "0.10.0-201721e" ::
          "0.10.0-2e89eb9" ::
          "0.10.0-09f5951" ::
          "0.10.0-b2fbb2f" ::
          Nil 
        order(versions)
      }
    }

    "parsing"-{
      // relaxed semantic version
      "major"-{
        assert(SemanticVersionParser("1") == Some(SemanticVersion(1)))
      }

      // relaxed semantic version
      "major.minor"-{
        assert(SemanticVersionParser("1.1") == Some(SemanticVersion(1, 1)))
      }

      "major.minor.patch"-{
        assert(SemanticVersionParser("1.1.1") == Some(SemanticVersion(1, 1, Some(1))))
      }

      "major.minor.patch-rc"-{
        assert(SemanticVersionParser("1.1.1-RC1") == Some(SemanticVersion(1, 1, Some(1), Some(ReleaseCandidate(1)))))
      }

      "major.minor.patch-m"-{
        assert(SemanticVersionParser("1.1.1-M1") == Some(SemanticVersion(1, 1, Some(1), Some(Milestone(1)))))
      }

      "major.minor.patch-xyz"-{
        assert(SemanticVersionParser("1.1.1-xyz") == Some(SemanticVersion(1, 1, Some(1), Some(OtherPreRelease("xyz")))))
      }

      "major.minor.patch+meta"-{
        assert(SemanticVersionParser("1.1.1+some.meta~data") == Some(SemanticVersion(1, 1, Some(1), None, Some("some.meta~data"))))
      }

      "git commit"-{
        assert(SemanticVersionParser("13e7afa9c1817d45b2989e545b2e9ead21d00cef") == None)
      }

      // we could support this one
      "epic.major.minor.patch"-{
        assert(SemanticVersionParser("1.1.1.1") == None)
      }

      // relaxed semantic version
      "v sufix"-{
        assert(SemanticVersionParser("v1") == Some(SemanticVersion(1)))
      }
    }
  }
}
import com.typesafe.sbt.SbtStartScript

name := "treadmill"

scalaVersion := "2.11.8"

seq(SbtStartScript.startScriptForClassesSettings: _*)

libraryDependencies ++= {
	Seq (
		"joda-time" % "joda-time" % "2.2",
		"org.joda" % "joda-convert" % "1.3.1"
	)
}

scalacOptions in Compile ++= Seq("-unchecked", "-deprecation", "-feature")

logLevel := Level.Info

offline := true

parallelExecution in Test := false

// After -o, "D" shows durations, "S" show short stacktraces, "F" shows full stacktraces
testOptions in Test += Tests.Argument("-oDS")

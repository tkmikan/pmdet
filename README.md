# PMDET
PMDET is a fuzzing tool for finding Android Parcelable deserialization mismatch vulnerabilities, i.e. [ReparcelBug](https://github.com/michalbednarski/ReparcelBug).

For more details, please refer to our [paper]().

## Tool setup and usage

PMDET is tested on Ubuntu 22.04 with **Java 17+**, but it should run on any modern Linux distrubition with **x64 CPU**.

[dex2jar](https://github.com/pxb1988/dex2jar) are required in `PATH`.

### Testcase Preparation
PMDET currently supports the detection of Android 12 and 13.
There are 3 steps to prepare the test input from either a firmware or a physical device.


#### 1. Identify `BOOTCLASSPATH`.
`BOOTCLASSPATH` is a environment variable concatenated by paths of boot class jars.
With a physical device, `adb shell 'echo $BOOTCLASSPATH'` will simply do.
With firmware only, one way is to collect and parse the protobuf files at `etc/classpaths` both in system partition and apex payloads; another way is to extract the strings of system odex files.

#### 2. Pull out boot class jars
Pull the boot class jars from the device or firmware into a writable directory in the host.
Jars whose paths start with `/apex` are only directly accessible for root, otherwise they should be extracted from apex payloads.
Note: the jars will be decompiled at the first run.

#### 3. Pull out `build.prop`
Pull out `/system/build.prop` from a rooted device or firmware, and place it with the jars.
For device without root, an alternative way is to reformat the output of `adb shell getprop`.


### CLI Options
```shell
$ java -jar PMDET-1.0-SNAPSHOT-all.jar
Missing required options: v, d
usage: Usage:
 -c,--class <class>                 the single class to fuzz. if omitted,
                                    all Parcelable classes are fuzzed
 -d,--bootclassdir <bootclassdir>   dir to the boot classes
 -o,--output <output>               the output (sqlite db)
 -t,--timeout <timeout>             the timeout seconds for each class,
                                    default to 30; 0 for no timeout
 -v,--androidVer <androidVer>       android version
```

Usage example:
```shell
# fuzz all Parcelable classes
java -jar PMDET-1.0-SNAPSHOT-all.jar -v 12 -d ~/path/to/jars/ -o result.db
# fuzz a single Parcelable classes
java -jar PMDET-1.0-SNAPSHOT-all.jar -v 12 -d ~/path/to/jars/ -o result.db com.a.b.c
```

## Build the tool
Get [jazzer_standalone.jar](https://github.com/CodeIntelligenceTesting/jazzer/releases), put it at project root, and run `./gradlew shadowJar`.

## Build Android lib
Please refer to the paper.


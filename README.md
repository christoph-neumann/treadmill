# Setup

Install Java. Java 8 is recommended.


# Getting Started

To build and run:

    ./run.sh

The first run is SLOW, but after that it's fast.

You can even symlink to it and it should "just work":

    ln -s ~/code/treadmill/run.sh ~/bin/treadmill
    treadmill

Run without any arguments to get the help text:

    Usage: treadmill [OPTIONS] [help] filename ...
    
    This program is used to keep track of hours worked.
    
    OPTIONS are one of:
     -h  display this help text
     -d  include the detailed report
    
     -i category,other,...
         include only the given categories in the report
    
     -x category,other,...
         exclude the given categories from the report
    
    The -i and -x options may be used more than once. The total list is the
    combination of all uses.
    
    It extracts the time information from the following format:
    
    Sat Mar 18 2000  10:00-13:00  meeting
    
    The fields are:
    - The date, including the day of the week
    - A time range in 24 hour time. It can span midnight, but cannot be longer than
      24 hours in length.
    - An optional category.

You can put a ":" in a category to get sub-categories.


# Docker image

To create a docker image:

    docker build -t treadmill .

To run the docker image:

    docker run --rm -v $(pwd):/data -w /data treadmill -d example.log


# Build Details

The script works by creating a single jar and running it. You can do this yourself with:

    ./sbt assembly
    java -jar target/scala-2.11/treadmill-assembly-0.1-SNAPSHOT.jar

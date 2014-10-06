package app

import java.io.File
import scala.io.Source
import org.joda.time.{DateTime, DateTimeConstants, Duration, LocalDate}


object Main {
	def main(argv: Array[String]) {

		def parseopt(argv: Seq[String], opts: Set[Opt], args: List[String]): (Set[Opt], List[String]) = {
			if (argv.size == 0) {
				(opts, args)
			} else {
				argv match {
					case "-h" :: tail => parseopt(tail, opts + Help(), args)
					case "-d" :: tail => parseopt(tail, opts + Detailed(), args)
					case "-i" :: list :: tail => parseopt(tail, opts + Include(list.split(",").toSet), args)
					case "-x" :: list :: tail => parseopt(tail, opts + Exclude(list.split(",").toSet), args)
					case arg :: tail => parseopt(tail, opts, args :+ arg)
				}
			}
		}

		val (opts, args) = parseopt(argv.toList, Set[Opt](), List[String]())

		// There maybe be more than one Include or Exclude set. We'll combine them into one master set
		// for each.
		val include = (opts collect { case Include(set) => set.toList }).flatten
		val exclude = (opts collect { case Exclude(set) => set.toList }).flatten

		if ( args.size == 0 || opts.contains(Help()) || args(0) == "help" ) {
			help()
			System.exit(1)
		}

		args foreach { filename => report(opts.contains(Detailed()), include, exclude, filename) }
	}


	def help() {
		val text =
"""
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
"""
		print(text)
	}

	
	val EntryLine = """^(\w{3} \w{3} \d{2} \d{4})  (\d\d:\d\d)-(\d\d:\d\d)(?:  ([a-zA-Z_/0-9:-]+))?(.*)$""".r
	val SuspiciousLine = """.*(Sun|Mon|Tue|Wed|Thu|Fri|Sat|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) .*""".r

	def group_name(str: String) = str.takeWhile(_ != ':')
	def total(entries: Seq[Entry]) = entries.map{_.hours}.foldLeft(0.0){_+_}

	def report(detailed: Boolean, include: Set[String], exclude: Set[String], filename: String) {
		// Parse out all the time entry lines from the file while checking for lines that might have
		// syntax errors. Since Source is lazy, use toList to force the iteration to complete.
		val in = Source.fromFile(new File(filename))
		val lines = (in.getLines flatMap {
			case EntryLine(date, from, to, cat_or_null, extra) =>
				val cat = Option(cat_or_null).getOrElse("uncategorized")
				// Create date + time objects. If the end time is before the start time, it means we crossed
				// midnight and the date is the next day. If the regex didn't match a category, it will be
				// "null", so we can use Option to set the default to "".
				if ( exclude.contains(cat) || (include.size > 0 && ! include.contains(cat)) ) {
					Seq()
				} else {
					val from_dt = format.parse_dt(date +" "+ from)
					val end = format.parse_dt(date +" "+ to)
					val to_dt = if (end.isBefore(from_dt)) end.plusDays(1) else end
					Seq(
						Entry(
							from_dt,
							to_dt,
							new Duration(from_dt, to_dt).getStandardMinutes() / 60.0,
							cat
						)
					)
				}

			case line @ SuspiciousLine(_) =>
				Seq(Suspicious(line))

			case _ => Seq()
		}).toList
		in.close

		val entries: List[Entry] = lines collect { case e: Entry => e }
		val suspicious: List[String] = lines collect { case Suspicious(line) => line }

		println("Time report for "+ filename)

		// A detailed report includes the details for each time entry.
		if ( detailed ) {
			println("")
			println("Start Date       Time Interval  Hours  Category")
			println("---------------  -------------  -----  --------")
			entries foreach { e =>
				println(format.summary(e))
			}
		}

		// List of categories that were used
		val categories = entries.map(_.category).distinct.sorted

		// Summarize the group information
		val group_total =
			entries
			.filter(_.category.contains(":"))
			.groupBy(e => group_name(e.category))
			.map { case (group, entries) =>
				(group, total(entries))
			}
		val group_cats = categories.groupBy(group_name)

		// Print out calendars for each category mentioned in the data.
		categories foreach { cat =>
			// Print the calendar for this category
			println("")
			println("-[ "+ cat + " ]-")
			print_calendar(entries filter {_.category == cat})

			// If this category is part of a group, and this is the last category in
			// that group, print out the group total.
			var group = group_name(cat)
			val cats = group_cats(group)
			if ( cats.size > 1 && cat == cats.last ) {
				println("\n%36s Group Total: %6.2f".format("", group_total(group)))
			}
		}

		val grand_total = total(entries)
		println("Grand Total: %.2f".format(grand_total))

		// Alert the user about any lines that look like they may have been time entries, but didn't
		// parse as one.
		if ( suspicious.size > 0 ) {
			println("\nWARNING: Supicious Lines:")
			suspicious foreach println
		}

		println()
	}


	/**
	 * Prints out a calendar for the list if time entries. The time entries are assumed to be in
	 * order. The calendar displays full weeks starting from the week including the first time entry
	 * up through the week containing the last time entry. Any empty weeks that are between entries
	 * will be included in the output. Each week will be labled with it's starting output and have a
	 * total for the week at the end.
	 * 
	 * Example:
	 *          Sun   Mon   Tue   Wed   Thu   Fri   Sat
	 * Mar 31    -     -     -     -   1.80  2.40  1.10    5.30
	 * Apr 07    -     -     -     -     -     -     -     0.00
	 * Apr 14    -     -   6.40    -     -     -     -     6.40
	 */
	def print_calendar(entries: Seq[Entry]) {
		val days = List("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
		println("%9s".format("") + days.mkString("   "))

		if ( entries.size == 0 ) return

		val by_day = entries groupBy { e => e.from.toLocalDate }
		val until = date.toSaturday(entries.last.from)

		var week_total = 0.0
		var total = 0.0
		var on = date.fromSunday(entries.head.from)
		while ( ! on.isAfter(until) ) {
			if ( on.getDayOfWeek() == DateTimeConstants.SUNDAY ) {
				print(format.short_date(on))
			}
			by_day.get(on) match {
				case None => print("%6s".format("- "))
				case Some(entries) =>
					val day_total = this.total(entries)
					week_total += day_total
					total += day_total
					print("%6.2f".format(day_total))
			}
			if ( on.getDayOfWeek() == DateTimeConstants.SATURDAY ) {
				println("%8.2f".format(week_total))
				week_total = 0
			}
			on = on.plusDays(1)
		}
		println("%42s Total: %6.2f".format("", total))
	}
}

sealed abstract trait Opt
case class Help() extends Opt
case class Detailed() extends Opt
case class Exclude(categories: Set[String]) extends Opt
case class Include(categories: Set[String]) extends Opt

case class Entry(
	from: DateTime,
	to: DateTime,
	hours: Double,
	category: String
)

case class Suspicious(
	line: String
)

object date {
	/**
	 * Returns the date of the Sunday right before the given date. If the given date is a Sunday, it
	 * will return the given date.
	 */
	def fromSunday(dt: DateTime): LocalDate = {
		val sun = DateTimeConstants.SUNDAY
		(if ( dt.getDayOfWeek == sun ) {
			dt
		} else if ( dt.withDayOfWeek(sun).isBefore(dt) ) {
			dt.withDayOfWeek(sun)
		} else {
			dt.minusWeeks(1).withDayOfWeek(sun)
		}).toLocalDate
	}

	/**
	 * Returns the date of the Saturday right after the given date. If the given date is a Saturday,
	 * it will return the given date.
	 */
	def toSaturday(dt: DateTime): LocalDate = {
		val sat = DateTimeConstants.SATURDAY
		(if ( dt.getDayOfWeek == sat ) {
			dt
		} else if ( dt.withDayOfWeek(sat).isAfter(dt) ) {
			dt.withDayOfWeek(sat)
		} else {
			dt.plusWeeks(1).withDayOfWeek(sat)
		}).toLocalDate
	}
}

object format {
	import org.joda.time.format.DateTimeFormat

	val date_format = DateTimeFormat.forPattern("EEE MMM dd yyyy")
	val date_iso_format = DateTimeFormat.forPattern("yyyy-MM-dd")
	val date_short_format = DateTimeFormat.forPattern("MMM dd")

	def date(dt: DateTime): String = date_format.print(dt)
	def iso_date(dt: DateTime): String = date_iso_format.print(dt)
	def short_date(dt: LocalDate): String = date_short_format.print(dt)

	val time_format = DateTimeFormat.forPattern("HH:mm")
	def time(dt: DateTime): String = time_format.print(dt)

	val dt_format = DateTimeFormat.forPattern("EEE MMM dd yyyy HH:mm")
	def parse_dt(s: String): DateTime = dt_format.parseDateTime(s)

	def summary(e: Entry): String = "%s  %s - %s  %5.2f  %s".format(date(e.from), time(e.from), time(e.to), e.hours, e.category)
}

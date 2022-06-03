# mysql-grammar-crawler

MySQL Grammar Crawler is a configurable SQL fuzzer that works by crawling the
[MySQL 8 ANTLR grammar maintained as part of Oracle's MySQL Workbench project](https://github.com/mysql/mysql-workbench/blob/8.0/library/parsers/grammars/MySQLParser.g4)
to generate a wide variety of statements with valid MySQL syntax. It was built to help expand the testing
for [Dolt DB](https://doltdb.com/) and to help measure compliance with MySQL's syntax. Generated statements are fed
into an existing SqlLogicTest runner and then those statements are run against Dolt DB as part of nightly automation.
The grammar crawler has already helped us identify several gaps in Dolt DB's MySQL compliance.

## Why build another SQL fuzzer?

SQL statement fuzzing is a well-established technique and there are many existing SQL fuzzers that are popular and work
well. At DoltHub, we already use multiple fuzzers as part of our testing (i.e. SqlLogicTest, DoltHub/fuzzer), but
mysql-grammar-crawler takes a different approach and compliments those existing tools. MySQL-Grammar-Crawler exploits
the fact that we have a fully defined grammar for the syntax we want to support, so it allows us to very thoroughly
explore that space.

# Get Crawling 🐛

Before we cover any more details, let's see some quick examples of the grammar crawler in action...

## Example: Generate All Drop Table Statements

This first example shows how to configure the crawler to perform a complete crawl on the `dropStatement` rule in the
MySQL grammar, with just a few rules skipped, completed statements output to stdout, and some summary statistics printed
out at the end.

Open up the `DropStatementsExample` class in the repo, run the `main` method, and you should see some results like this:

```text
DROP UNDO TABLESPACE "text0";
DROP UNDO TABLESPACE "text1" ENGINE "text2" ENGINE "text3";
DROP UNDO TABLESPACE "text4" ENGINE "text5" ENGINE 'text6';
DROP UNDO TABLESPACE "text7" ENGINE "text8" ENGINE "text9";
DROP UNDO TABLESPACE "text10" ENGINE "text11" ENGINE `t5`;
...
DROP DATABASE IF EXISTS "text21636";
DROP DATABASE IF EXISTS `t3a33`;
DROP DATABASE IF EXISTS t3a34;

Total Statements: 14,900
Literal Element Coverage: 
 - Total:    47
 - Used:     47
 - Unused:   0
 - Frequent: 31
 - Coverage: 100.00%
```

## Example: Generate Create Table Statements

This next example is a bit more involved. The syntax for `create table` statements is much more complex than the syntax
for `drop` statements, so this example has more skipped rules to prune the crawl space. This example also uses a
different `CrawlStrategy` – instead of crawling every possible path (i.e. exploring the full syntax space), it uses
the `CoverageAwareCrawlStrategy` to try and make decisions about whether to crawl a path in the grammar or not based on
the current coverage of the grammar. It also demonstrates the configuration option `XXX`, which allows you to cap the
maximum
number of statements generated by the crawler. Finally, this example also shows the `SQLLogicProtoStatementWriter` to
output the generated statements in a format ready
to be consumed by SqlLogicTest.

Open up the `CreateTableStatementsExample` class in the repo, run the `main` method, and you should see some results
similar to this:

```text
CREATE TABLE `t1` (LIKE `c1`);
CREATE TABLE `t2` (LIKE c1);
CREATE TABLE `t3` LIKE `c1`;
...
CREATE TABLE tc34e (c1 MEDIUMINT ZEROFILL NOT NULL, c2 NCHAR VARYING (12) NULL);
CREATE TABLE tc34f (c1 MEDIUMINT ZEROFILL NOT NULL, c2 NATIONAL CHAR VARYING (11) UNIQUE);
CREATE TABLE tc350 (c1 MEDIUMINT ZEROFILL NOT NULL, c2 NATIONAL CHAR VARYING (16) UNIQUE KEY);

Total Statements: 50,000
Literal Element Coverage: 
 - Total:    104
 - Used:     78
 - Unused:   26
 - Frequent: 77
 - Coverage: 75.00%
```

# Crawler Components

## Crawler Configuration

The crawler can be configured to skip parts of the grammar, to control how the crawler selects paths in the grammar
graph to crawl, the maximum number of statements to generate, the output format for generated statements, and more.

## Crawl Strategies

The crawler supports three crawl strategies:

* Full Crawl – every path through the grammar graph will be explored, with some caveats (e.g. cycles are detected and
  skipped). This mode works well for small grammars or small subsets of a grammar, but can quickly produce a LOT of
  generated statement templates.

## Reification

After the crawler finishes a complete path through the grammar graph and has generated the template for a valid
statement, it reifies the template into a valid SQL statement. This involves plugging in literal values for placeholders
in the template and doing any minor cleanup to the statement to help increase the chances of it executing cleanly.

## Statement Output

After a statement has been reified, it is sent to the `StatementWriter` configured for the Crawler. There are currently
two implementations of `StatementWriter` available:

* `StdOutStatementWriter` – This implementation simply outputs the statement directly to StdOut.
* `SQLLogicProtoStatementWriter` – This implementation writes out a proto format suitable
  for [SqlLogicTest](https://www.sqlite.org/sqllogictest/doc/trunk/about.wiki) to process.

# Contributions

We're happy to accept contributions if you want to use the grammar crawler in your work and need additional behavior.
Feel free to cut issues or Pull Requests or [come join the Dolt DB Discord server](https://discord.com/invite/RFwfYpu)
and chat with us about ideas.


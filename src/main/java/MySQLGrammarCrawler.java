import java.util.*;

public class MySQLGrammarCrawler {
    private static Set<String> rulesToSkip = new HashSet<>();

    private static Map<String, Rules.Rule> ruleMap;

    public static Crawler crawler = new Crawler();

    private static CrawlStrategies.CrawlStrategy crawlStrategy;

    public static void main(String[] args) throws Exception {
        ruleMap = MySQLGrammarUtils.loadMySQLGrammarRules();

        generateCreateTableStatements();
    }

    private static void generateCreateTableStatements() {
        Rules.Rule rule = ruleMap.get("createTable");

        // Configure crawling rules...
        // Skipping these rules to simplify the output and to make it easier to plug in identifier tokens
        rulesToSkip.add("dotIdentifier");
//        rulesToSkip.add("identifierKeyword");
//        rulesToSkip.add("fieldIdentifier");

        // Disabling these to limit crawler's output for CreateTable
        rulesToSkip.add("procedureAnalyseClause");
        rulesToSkip.add("expr");
        rulesToSkip.add("queryExpression");
        rulesToSkip.add("queryExpressionOrParens");
        rulesToSkip.add("partitionClause");
        rulesToSkip.add("createTableOptions");
        rulesToSkip.add("tableConstraintDef");
        rulesToSkip.add("spatialIndexOption");
        rulesToSkip.add("fulltextIndexOption");

        crawlStrategy = new CrawlStrategies.FullCrawl();
//        crawlStrategy = new CrawlStrategies.RandomCrawl();

        crawler.setStatementPrefix("CREATE ");
        crawler.setStatementWriter(new StdOutStatementWriter());
//        crawler.setStatementWriter(new SQLLogicProtoStatementWriter("sqllogic-test.proto"););

        crawler.startCrawl(rule);
    }

    public static void processElement(CrawlContext currentContext) {
        Rules.Element element = currentContext.elementToProcess;
        TemplateBuffer generatedTemplate = currentContext.generatedTemplate;

//        if (true) {
//            long heapSize = Runtime.getRuntime().totalMemory();
//            long heapMaxSize = Runtime.getRuntime().maxMemory();
//            System.out.println("Max Heap: " + heapMaxSize);
//            System.out.println("Current Heap: " + heapSize);
//        }

        if (element.isOptional()) {
            if (!currentContext.includeOptional) {
                // Here is where we fork off another separate crawler thread, including its own buffer to track its unique output
                if (crawlStrategy.shouldCrawl()) {
                    CrawlContext newContext = crawler.forkCrawl(currentContext, element);
                    newContext.includeOptional = true;
                    newContext.parentPath.addAll(currentContext.parentPath);
                }

                if (currentContext.futureElements.isEmpty() == false) {
                    CrawlContext.FutureElementContext futureElementContext = currentContext.futureElements.pop();
                    CrawlContext crawlContext = crawler.continueCrawl(currentContext, futureElementContext.element);
                    crawlContext.parentPath.addAll(futureElementContext.parentPath);
                } else {
                    // At this point, we know the current template is fully complete and done generating
//                    System.out.println(" : " + currentContext.generatedTemplate);
                    crawler.statementCompleted(currentContext.generatedTemplate);
                }

                // Then return to avoid any processing this optional element on this fork
                return;
            }
        }

        if (element instanceof Rules.LiteralElement) {
            generatedTemplate.addElement(element);
        } else if (element instanceof Rules.ElementGroup) {
            Rules.ElementGroup group = (Rules.ElementGroup) element;

            // TODO: This logic for translating an ElementGroup into a list of choices is super hacky.
            //       The parser should take care of this when it analyzes the ANTLR grammar and return
            //       Choice instead of ElementGroup so that we don't have to do any of this here.
            //       But... this is working now, and not the highest priority to fix.
            List<Rules.Element> choices = new ArrayList<>();
            Rules.ElementGroup currentGroup = new Rules.ElementGroup();
            boolean isChoice = false;
            for (Rules.Element e : group.elements) {
                if (e instanceof Rules.SeparatorElement) {
                    isChoice = true;
                    choices.add(currentGroup);
                    currentGroup = new Rules.ElementGroup();
                } else {
                    currentGroup.elements.add(e);
                }
            }
            if (currentGroup.elements.isEmpty() == false) {
                choices.add(currentGroup);
                currentGroup = null;
            }

            if (isChoice) {
                boolean firstChoice = true;
                for (Rules.Element e : choices) {
                    if (crawlStrategy.shouldCrawl() == false) continue;

                    if (firstChoice) {
                        // The first choice uses the current template buffer.
                        // All additional choices get a forked template buffer.
                        CrawlContext crawlContext = crawler.continueCrawl(currentContext, e);
                        crawlContext.parentPath.addAll(currentContext.parentPath);
                        firstChoice = false;
                    } else {
                        // all other choices get a new/forked template buffer
                        CrawlContext crawlContext = crawler.forkCrawl(currentContext, e);
                        crawlContext.parentPath.addAll(currentContext.parentPath);
                    }
                }

                // If the crawl strategy didn't select any choice to crawl, abort the current crawl path
                if (firstChoice) currentContext.abort();

                return;
            } else {
                CrawlContext newContext = crawler.continueCrawl(currentContext, group.elements.get(0));
                newContext.parentPath.addAll(currentContext.parentPath);
                for (int i = group.elements.size() - 1; i >= 1; i--) {
                    Rules.Element e = group.elements.get(i);
                    CrawlContext.FutureElementContext futureElementContext = new CrawlContext.FutureElementContext(e);
                    futureElementContext.parentPath.addAll(currentContext.parentPath);
                    newContext.futureElements.push(futureElementContext);
                }
                return;
            }
        } else if (element instanceof Rules.RuleRefElement) {
            Rules.RuleRefElement ruleref = (Rules.RuleRefElement) element;
            Rules.Rule rule = ruleMap.get(ruleref.getName());

            // TODO: Instead of just testing contains... we want to have a limit on how many times we recurse through a rule
            //       but... shouldn't the block below prevent cycles?
            if (rulesToSkip.contains(rule.name)) {
                currentContext.abort();
                return;
            }

            if (currentContext.parentPath.contains(rule.name)) {
                // TODO: We need better control of cycles... for example, instead
                //       of restricting any cycles, we may want to allow one cycle, but not two, per rule.
                currentContext.abort();
                return;
            }

            currentContext.parentPath.add(rule.name);

            boolean first = true;
            for (Rules.Alternative alternative : rule.alternatives) {
                if (first) {
                    CrawlContext newContext = crawler.continueCrawl(currentContext, alternative.elements.get(0));
                    newContext.parentPath.addAll(currentContext.parentPath);
                    first = false;
                } else {
                    // dataType explodes our crawl too much, so if we hit this rule, only include the first alternative
                    if (rule.name.equals("dataType") == false) continue;
                    // TODO: For now, we skip following rule alternatives (other than the first alternative), since
                    //       it explodes the crawl space and blows the heap.
//                    CrawlContext newContext = forkCrawl(currentContext, alternative.elements.get(0));
//                    newContext.parentPath.addAll(currentContext.parentPath);
                }
            }
            return;
        } else {
            throw new RuntimeException("Unexpected element type: " + element.getClass());
        }

        // TODO: These don't ever even get here because of all the early returns
        if (element.isRepeated()) { // *
            // TODO: For any-number-of-times elements... we want to generate:
            //       one template form without the repeated element
            //       one template form with the repeated element once
            //       one template form with the repeated element twice
            // TODO: Processing each element once by default, but controllable through CrawlContext
            //          crawlContext.processCount = 2;
            //          crawlContext.processCount = 0;
            ///      That could enable this code to be pulled up to the start of this method, with optional handling
        } else if (element.isOnceOrMore()) { // +
            // include once-or-more elements just once, for now
            // TODO: for once-or-more elements, we want to generate:
            //       one template form with the element included once
            //       one template form with the element included twice
        }

        // Queue up the next element to be processed from our stack
        if (currentContext.futureElements.isEmpty()) {
            // At this point, we know a rule should be fully complete
//            System.out.println(" : " + currentContext.generatedTemplate);
            crawler.statementCompleted(currentContext.generatedTemplate);
        } else {
            CrawlContext.FutureElementContext futureElementContext = currentContext.futureElements.pop();
            CrawlContext crawlContext = crawler.continueCrawl(currentContext, futureElementContext.element);
            crawlContext.parentPath.addAll(futureElementContext.parentPath);
        }
    }

}

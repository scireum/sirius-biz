/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer

class ImportDictionaryTest extends BaseSpecification {

    def "detectHeaderProblems detects a skipped column"() {
        when:
        ImportDictionary dict = new ImportDictionary()
        dict.useMapping(Arrays.asList("A", "B", "C", "D", "E"))
        and:
        def problems = new ArrayList()
        then:
        dict.detectHeaderProblems(Values.of("A", "C", "D", "E"), { problem, _ -> problems.add(problem) }, true)
        and:
        problems.get(0) == "Die Spalte 2 ('B') fehlt."
        problems.size() == 1
    }

    def "detectHeaderProblems detects two skipped columns"() {
        when:
        ImportDictionary dict = new ImportDictionary()
        dict.useMapping(Arrays.asList("A", "B", "C", "D", "E"))
        and:
        def problems = new ArrayList()
        then:
        dict.detectHeaderProblems(Values.of("A", "D", "E"), { problem, _ -> problems.add(problem) }, true)
        and:
        problems.get(0) == "Die Spalte 2 ('B') fehlt."
        problems.get(1) == "Die Spalte 3 ('C') fehlt."
        problems.size() == 2
    }

    def "detectHeaderProblems detects a superfluous column"() {
        when:
        ImportDictionary dict = new ImportDictionary()
        dict.useMapping(Arrays.asList("A", "B", "C"))
        and:
        def problems = new ArrayList()
        then:
        dict.detectHeaderProblems(Values.of("A", "A1", "B", "C"), { problem, _ -> problems.add(problem) }, true)
        and:
        problems.get(0) == "Die Spalte 2 ('A1') ist unerwartet."
        problems.size() == 1
    }

    def "detectHeaderProblems detects two superfluous columns"() {
        when:
        ImportDictionary dict = new ImportDictionary()
        dict.useMapping(Arrays.asList("A", "B", "C"))
        and:
        def problems = new ArrayList()
        then:
        dict.detectHeaderProblems(Values.of("A", "A1", "A2", "B", "C"), { problem, _ -> problems.add(problem) }, true)
        and:
        problems.get(0) == "Die Spalte 2 ('A1') ist unerwartet."
        problems.get(1) == "Die Spalte 3 ('A2') ist unerwartet."
        problems.size() == 2
    }

    def "detectHeaderProblems detects wrong column"() {
        when:
        ImportDictionary dict = new ImportDictionary()
        dict.useMapping(Arrays.asList("A", "B", "C", "D"))
        and:
        def problems = new ArrayList()
        then:
        dict.detectHeaderProblems(Values.of("A", "F", "G", "H"), { problem, _ -> problems.add(problem) }, true)
        and:
        problems.get(0) == "In der Spalte 2 wurde 'B' erwartet, aber 'F' gefunden."
        problems.get(1) == "In der Spalte 3 wurde 'C' erwartet, aber 'G' gefunden."
        problems.get(2) == "In der Spalte 4 wurde 'D' erwartet, aber 'H' gefunden."
        problems.size() == 3
    }

    def "detectHeaderProblems warns for aliased columns"() {
        when:
        ImportDictionary dict = new ImportDictionary()
        dict.addField(FieldDefinition.stringField("A", 25).addAlias("XA"))
        dict.useMapping(Arrays.asList("A", "B", "C", "D"))
        and:
        def problems = new ArrayList()
        def problemDetected = dict.detectHeaderProblems(Values.of("XA", "B", "C", "D"), { problem, errorFlag ->
        problems.add(Tuple.create(problem, errorFlag))
    }, true)
        then:
        problemDetected == false
        and:
        problems.get(0).getFirst() == "In der Spalte 1 wurde 'A' erwartet, aber 'XA' gefunden."
        problems.get(0).getSecond() == false
        problems.size() == 1
    }

    def "detectHeaderProblems detects additional columns"() {
        when:
        ImportDictionary dict = new ImportDictionary()
        dict.useMapping(Arrays.asList("A", "B", "C"))
        and:
        def problems = new ArrayList()
        then:
        dict.detectHeaderProblems(Values.of("A", "B", "C", "D"), { problem, _ -> problems.add(problem) }, true)
        and:
        problems.get(0) == "Die Spalte 4 ('D') ist unerwartet."
        problems.size() == 1
    }

    def "detectHeaderProblems detects missing columns"() {
        when:
        ImportDictionary dict = new ImportDictionary()
        dict.useMapping(Arrays.asList("A", "B", "C", "D"))
        and:
        def problems = new ArrayList()
        then:
        dict.detectHeaderProblems(Values.of("A", "B", "C"), { problem, _ -> problems.add(problem) }, true)
        and:
        problems.get(0) == "Die Spalte 4 ('D') fehlt."
        problems.size() == 1
    }
}

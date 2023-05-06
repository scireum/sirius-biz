/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

import com.googlecode.junittoolbox.SuiteClasses;
import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.runner.RunWith;
import sirius.kernel.ScenarioSuite;

// JUnit 4 annotations below
@RunWith(ScenarioSuite.class)
@SuiteClasses({"**/*Test.class", "**/*Spec.class"})
// JUnit 5 annotations below
@Suite
@IncludeClassNamePatterns({"^.*Test$", "^.*Spec$"})
@SelectPackages("sirius")
public class TestSuite {

}

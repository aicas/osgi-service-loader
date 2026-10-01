package com.aicas.osgi.spi.realworld.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class KnownFailureCondition implements ExecutionCondition
{
  private static final String RESOURCE = "/known-failures.txt";

  private static final Map<String, String> KNOWN_FAILURES =
      loadKnownFailures();

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(
      ExtensionContext context)
  {
    Class<?> testClass = context.getTestClass().orElse(null);
    if (testClass == null)
      {
        return ConditionEvaluationResult.enabled("Not a test class");
      }

    String classId = testClass.getName() + "#*";
    String classReason = KNOWN_FAILURES.get(classId);
    if (classReason != null)
      {
        return ConditionEvaluationResult.disabled("Known failure: " + classReason);
      }

    Method testMethod = context.getTestMethod().orElse(null);
    if (testMethod == null)
      {
        return ConditionEvaluationResult.enabled("Test class is not disabled");
      }

    String methodId = testClass.getName() + "#" + testMethod.getName();
    String methodReason = KNOWN_FAILURES.get(methodId);
    if (methodReason != null)
      {
        return ConditionEvaluationResult.disabled("Known failure: " + methodReason);
      }

    return ConditionEvaluationResult.enabled("Not listed in " + RESOURCE);
  }

  private static Map<String, String> loadKnownFailures()
  {
    InputStream input = KnownFailureCondition.class.getResourceAsStream(RESOURCE);
    if (input == null)
      {
        return Collections.emptyMap();
      }

    Map<String, String> failures = new LinkedHashMap<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input,
        StandardCharsets.UTF_8)))
      {
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null)
          {
            lineNumber++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
              {
                continue;
              }

            int separator = line.indexOf('|');
            String testId;
            String reason;
            if (separator < 0)
              {
                testId = line;
                reason = "Listed in " + RESOURCE;
              }
            else
              {
                testId = line.substring(0, separator).trim();
                reason = line.substring(separator + 1).trim();
              }

            if (testId.isEmpty())
              {
                throw new IllegalStateException("Empty test identifier at " +
                    RESOURCE + ":" + lineNumber);
              }

            String previous = failures.putIfAbsent(testId, reason);
            if (previous != null)
              {
                throw new IllegalStateException("Duplicate test identifier " +
                    testId + " at " + RESOURCE + ":" + lineNumber);
              }
          }
      }
    catch (IOException e)
      {
        throw new ExceptionInInitializerError(e);
      }

    return Collections.unmodifiableMap(failures);
  }
}

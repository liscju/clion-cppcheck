package com.github.johnthagen.cppcheck;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CppcheckInspection extends LocalInspectionTool {
  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file,
                                       @NotNull InspectionManager manager,
                                       boolean isOnTheFly) {
    List<ProblemDescriptor> descriptors = new ArrayList<>();

    String cppcheckPath = Properties.get(Configuration.CONFIGURATION_KEY_CPPCHECK_PATH);
    String cppcheckOptions = Properties.get(Configuration.CONFIGURATION_KEY_CPPCHECK_OPTIONS);

    if (!isCFamilyFile(file)) {
      return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
    }

    if (cppcheckPath == null || cppcheckPath.isEmpty()) {
      StatusBar.Info.set("[!] Error: Please set path of cppcheck in File->Settings->Other Settings",
                         file.getProject());
      return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
    }

    Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
    if (document == null || document.getLineCount() == 0) {
      return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
    }

    try {
      String cppcheckOutput = executeCommandOnFile(cppcheckPath, cppcheckOptions, file);

      if (cppcheckOutput.isEmpty()) {
        return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
      }
      Scanner scanner = new Scanner(cppcheckOutput);

      //Notifications.Bus.notify(new Notification("cppcheck",
      //                                          "Info",
      //                                          file.getVirtualFile().getCanonicalPath() + "\n" +
      //                                          cppcheckOutput,
      //                                          NotificationType.INFORMATION));

      // Example output line:
      // [C:\Users\John Hagen\ClionProjects\test\main.cpp:12]: (style) Variable 'a' is not assigned a value.
      Pattern pattern = Pattern.compile("^\\[.+:(\\d+)\\]:\\s+\\((\\w+)\\)\\s+(.+)");

      String line;
      while (scanner.hasNext()) {
        line = scanner.nextLine();
        Matcher matcher = pattern.matcher(line);

        if (!matcher.matches()) {
          continue;
        }

        int lineNumber = Integer.parseInt(matcher.group(1), 10);
        final String severity = matcher.group(2);
        final String errorMessage = matcher.group(3);

        // Document counts lines starting at 0, rather than 1 like in cppcheck.
        lineNumber -= 1;

        final int lineStartOffset = document.getLineStartOffset(lineNumber);
        final int lintEndOffset = document.getLineEndOffset(lineNumber);

        ProblemDescriptor problemDescriptor = manager.createProblemDescriptor(
          file,
          TextRange.create(lineStartOffset, lintEndOffset),
          "cppcheck: (" + severity + ") " + errorMessage,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          true);
        descriptors.add(problemDescriptor);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  private static String executeCommandOnFile(String command,
                                             String options,
                                             @NotNull PsiFile file) throws IOException {
    final String executionString = "\"" + command + "\" "+
                                   options + " " +
                                   "\"" + file.getVirtualFile().getCanonicalPath() + "\"";

    final Process process = Runtime.getRuntime().exec(executionString);

    final StringBuilder errString = new StringBuilder();
    Thread errorThread = new Thread(new Runnable() {
      @Override
      public void run() {
        BufferedReader errStream = new BufferedReader(new InputStreamReader(
          process.getErrorStream()));
        String line;
        try {
          while ((line = errStream.readLine()) != null) {
            errString.append(line).append("\n");
          }
        } catch (IOException ex) {
          ex.printStackTrace();
        } finally {
          try {
            errStream.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    });
    errorThread.start();

    try {
      errorThread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    return errString.toString();
  }

  private static boolean isCFamilyFile(@NotNull PsiFile file){
    final String lowerFileExtension = file.getVirtualFile().getExtension().toLowerCase();
    if (lowerFileExtension.equals("c") ||
        lowerFileExtension.equals("cc") ||
        lowerFileExtension.equals("cp") ||
        lowerFileExtension.equals("cpp") ||
        lowerFileExtension.equals("c++") ||
        lowerFileExtension.equals("cxx") ||
        lowerFileExtension.equals("h") ||
        lowerFileExtension.equals("hh") ||
        lowerFileExtension.equals("hpp")) {
      return true;
    } else {
      return false;
    }
  }
}
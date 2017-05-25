package org.toradocu.extractor;

import static java.util.stream.Collectors.toList;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.nodeTypes.modifiers.NodeWithPrivateModifier;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import org.toradocu.util.Reflection;

/** {@code JavadocExtractor} extracts {@code ExecutableMember}s from a class. */
public final class JavadocExtractor {

  /**
   * Returns a list of {@code ExecutableMember}s extracted from the class with name {@code
   * className}.
   *
   * @param className the qualified class name of the class from which to extract documentation
   * @param sourcePath the path to the project source root folder
   * @return a list of documented executable members
   * @throws ClassNotFoundException if some reflection information cannot be loaded
   * @throws FileNotFoundException if the source code of the class with name {@code className}
   *     cannot be found in path {@code sourcePath}
   */
  public List<ExecutableMember> extract(String className, String sourcePath)
      throws ClassNotFoundException, FileNotFoundException {

    // Obtain executable members by means of reflection.
    final Class<?> clazz = Reflection.getClass(className);
    final List<Executable> reflectionExecutables = getExecutables(clazz);

    // Obtain executable members in the source code.
    // TODO Add support for nested classes.
    String classFile = sourcePath + "/" + className.replaceAll("\\.", "/") + ".java";
    final List<CallableDeclaration<?>> sourceExecutables =
        getExecutables(clazz.getSimpleName(), classFile);

    // Map reflection executable members to corresponding source members.
    Map<Executable, CallableDeclaration<?>> executablesMap =
        mapExecutables(reflectionExecutables, sourceExecutables);

    // Create the list of ExecutableMembers.
    List<ExecutableMember> members = new ArrayList<>(reflectionExecutables.size());
    for (Entry<Executable, CallableDeclaration<?>> entry : executablesMap.entrySet()) {
      final Executable reflectionMember = entry.getKey();
      final CallableDeclaration<?> sourceMember = entry.getValue();
      final List<Parameter> parameters =
          getParameters(sourceMember.getParameters(), reflectionMember.getParameters());
      List<org.toradocu.extractor.Tag> tags = createTags(sourceMember, parameters);
      members.add(new ExecutableMember(reflectionMember, parameters, tags));
    }
    return members;
  }

  private List<org.toradocu.extractor.Tag> createTags(
      CallableDeclaration<?> sourceMember, List<Parameter> parameters)
      throws ClassNotFoundException {
    List<org.toradocu.extractor.Tag> tags = new ArrayList<>();
    final Optional<Javadoc> javadocOpt = sourceMember.getJavadoc();
    if (javadocOpt.isPresent()) {
      final Javadoc javadocComment = javadocOpt.get();
      final List<JavadocBlockTag> blockTags = javadocComment.getBlockTags();
      for (JavadocBlockTag blockTag : blockTags) {
        org.toradocu.extractor.Tag newTag = null;
        switch (blockTag.getType()) {
          case PARAM:
            newTag = createParamTag(blockTag, parameters);
            break;
          case RETURN:
            newTag = createReturnTag(blockTag);
            break;
          case THROWS:
            newTag = createThrowsTag(blockTag, sourceMember);
            break;
        }
        if (newTag != null) {
          tags.add(newTag);
        }
      }
    }
    return tags;
  }

  private ThrowsTag createThrowsTag(JavadocBlockTag blockTag, CallableDeclaration<?> sourceMember)
      throws ClassNotFoundException {
    // Javaparser library does not provide a nice parsing of @throws tags. We have to parse the
    // comment text by ourselves.
    String comment = blockTag.getContent().toText();
    final String[] tokens = comment.split(" ", 2);
    final String exceptionName = tokens[0];
    Class<?> exceptionType = findExceptionType(sourceMember, exceptionName);
    return new ThrowsTag(exceptionType, tokens[1]);
  }

  private ReturnTag createReturnTag(JavadocBlockTag blockTag) {
    return new ReturnTag(blockTag.getContent().toText());
  }

  private ParamTag createParamTag(JavadocBlockTag blockTag, List<Parameter> parameters) {
    String paramName = blockTag.getName().orElse("");

    final List<Parameter> matchingParams =
        parameters.stream().filter(p -> p.getName().equals(paramName)).collect(toList());
    // TODO If paramName not present in paramNames => issue a warning about incorrect documentation!
    // TODO If more than one matching parameter found => issue a warning about incorrect documentation!
    return new ParamTag(matchingParams.get(0), blockTag.getContent().toText());
  }

  private List<Parameter> getParameters(
      NodeList<com.github.javaparser.ast.body.Parameter> sourceParams,
      java.lang.reflect.Parameter[] reflectionParams) {

    if (sourceParams.size() != reflectionParams.length) {
      throw new IllegalArgumentException(
          "Source param types and reflection param types should be of the same size.");
    }

    List<Parameter> parameters = new ArrayList<>(sourceParams.size());
    for (int i = 0; i < sourceParams.size(); i++) {
      final Class<?> paramType = reflectionParams[i].getType();
      final com.github.javaparser.ast.body.Parameter parameter = sourceParams.get(i);
      final Boolean nullable = isNullable(parameter);
      final String paramName = parameter.getName().asString();
      parameters.add(new Parameter(paramType, paramName, nullable));
    }
    return parameters;
  }

  // Checks whether the given parameter is annotated with @NotNull or @Nullable or similar.
  private Boolean isNullable(com.github.javaparser.ast.body.Parameter parameter) {
    final List<String> parameterAnnotations =
        parameter.getAnnotations().stream().map(a -> a.getName().asString()).collect(toList());
    List<String> notNullAnnotations = new ArrayList<>(parameterAnnotations);
    notNullAnnotations.retainAll(Parameter.notNullAnnotations);
    List<String> nullableAnnotations = new ArrayList<>(parameterAnnotations);
    nullableAnnotations.retainAll(Parameter.nullableAnnotations);

    if (!notNullAnnotations.isEmpty() && !nullableAnnotations.isEmpty()) {
      // Parameter is annotated as both nullable and notNull.
      // TODO Log a warning about wrong specification?
      return null;
    }
    if (!notNullAnnotations.isEmpty()) {
      return false;
    }
    if (!nullableAnnotations.isEmpty()) {
      return true;
    }
    return null;
  }

  // Collects members through reflection.
  private List<Executable> getExecutables(Class<?> clazz) {
    List<Executable> executables = new ArrayList<>();
    executables.addAll(Arrays.asList(clazz.getDeclaredConstructors()));
    executables.addAll(Arrays.asList(clazz.getDeclaredMethods()));
    executables.removeIf(e -> Modifier.isPrivate(e.getModifiers())); // Ignore private members.
    return Collections.unmodifiableList(executables);
  }

  // Collects members from source code.
  private List<CallableDeclaration<?>> getExecutables(String className, String sourcePath)
      throws FileNotFoundException {
    final CompilationUnit cu = JavaParser.parse(new File(sourcePath));
    final Optional<ClassOrInterfaceDeclaration> sourceClassOpt = cu.getClassByName(className);
    final List<CallableDeclaration<?>> sourceExecutables = new ArrayList<>();
    if (sourceClassOpt.isPresent()) {
      final ClassOrInterfaceDeclaration sourceClass = sourceClassOpt.get();
      sourceExecutables.addAll(sourceClass.getConstructors());
      sourceExecutables.addAll(sourceClass.getMethods());
      sourceExecutables.removeIf(NodeWithPrivateModifier::isPrivate); // Ignore private members.
    }
    return Collections.unmodifiableList(sourceExecutables);
  }

  // Maps reflection members to source code members.
  private Map<Executable, CallableDeclaration<?>> mapExecutables(
      List<Executable> reflectionExecutables, List<CallableDeclaration<?>> sourceExecutables) {

    if (reflectionExecutables.size() != sourceExecutables.size()) {
      throw new IllegalArgumentException("Error: Provided lists have different size.");
    }

    Map<Executable, CallableDeclaration<?>> map = new LinkedHashMap<>(reflectionExecutables.size());
    for (CallableDeclaration<?> sourceMember : sourceExecutables) {
      final List<Executable> matches =
          reflectionExecutables
              .stream()
              .filter(
                  e ->
                      executableMemberSimpleName(e.getName())
                              .equals(sourceMember.getName().asString())
                          && sameParTypes(e.getParameters(), sourceMember.getParameters()))
              .collect(toList());
      if (matches.size() < 1) {
        throw new AssertionError(
            "Cannot find reflection executable member corresponding to "
                + sourceMember.getSignature());
      }
      if (matches.size() > 1) {
        throw new AssertionError(
            "Found multiple reflection executable members corresponding to "
                + sourceMember.getSignature());
      }
      map.put(matches.get(0), sourceMember);
    }
    return map;
  }

  // Checks that reflection param types and source param types are the same.
  private boolean sameParTypes(
      java.lang.reflect.Parameter[] reflectionParams,
      NodeList<com.github.javaparser.ast.body.Parameter> sourceParams) {
    if (reflectionParams.length != sourceParams.size()) {
      return false;
    }

    for (int i = 0; i < reflectionParams.length; i++) {
      final java.lang.reflect.Parameter reflectionParam = reflectionParams[i];
      final String reflectionType = removeGenerics(reflectionParam.getType().getSimpleName());

      final com.github.javaparser.ast.body.Parameter sourceParam = sourceParams.get(i);
      final String sourceType = removeGenerics(sourceParam.getType().asString());

      if (!reflectionType.equals(sourceType)) {
        return false;
      }
    }

    return true;
  }

  private String removeGenerics(String type) {
    int i = type.indexOf("<");
    if (i != -1) { // If type contains "<".
      return type.substring(0, i);
    }
    return type;
  }

  private String executableMemberSimpleName(String reflectionName) {
    // Constructor names contain package name.
    int i = reflectionName.indexOf(".");
    if (i != -1) {
      return reflectionName.substring(reflectionName.lastIndexOf(".") + 1);
    }
    return reflectionName;
  }

  // Tries
  private Class<?> findExceptionType(CallableDeclaration<?> sourceMember, String exceptionName)
      throws ClassNotFoundException {
    Class<?> exceptionType = null;
    try {
      exceptionType = Reflection.getClass(exceptionName);
    } catch (ClassNotFoundException e) {
      // Intentionally empty.
    }
    if (exceptionType == null) {
      try {
        exceptionType = Reflection.getClass("java.lang." + exceptionName);
      } catch (ClassNotFoundException e) {
        // Intentionally empty.
      }
    }
    if (exceptionType == null) {
      // Look for an import statement to complete exception type name.
      Optional<Node> nodeOpt = sourceMember.getParentNode();
      Node node = nodeOpt.orElse(null);
      while (!(node instanceof CompilationUnit)) {
        nodeOpt = node.getParentNode();
        node = nodeOpt.orElse(null);
      }
      CompilationUnit cu = (CompilationUnit) node;
      final NodeList<ImportDeclaration> imports = cu.getImports();
      for (ImportDeclaration anImport : imports) {
        String importedType = anImport.getNameAsString();
        if (importedType.contains(exceptionName)) {
          exceptionType = Reflection.getClass(importedType);
        }
      }
    }
    if (exceptionType == null) {
      throw new ClassNotFoundException("Impossible to load exception type " + exceptionName);
    }
    return exceptionType;
  }
}

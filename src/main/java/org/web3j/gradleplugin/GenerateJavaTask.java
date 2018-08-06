package org.web3j.gradleplugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.solidity.compiler.SolidityCompiler.Options;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.web3j.codegen.SolidityFunctionWrapper;

public class GenerateJavaTask extends SourceTask {

    private static final String DEFAULT_GENERATED_PACKAGE = "org.web3j.model";

    private static final boolean NATIVE_JAVA_TYPE = true;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Input
    private String generatedJavaPackageName = DEFAULT_GENERATED_PACKAGE;

    @TaskAction
    @SuppressWarnings("unused")
    void actionOnAllContracts() throws Exception {
        for (final File contractFile : getSource()) {
            final String contractPath = contractFile.getAbsolutePath();
            getProject().getLogger().info("\tAction on contract '" + contractPath + "'");
            actionOnOneContract(contractPath);
        }
    }

    private void actionOnOneContract(final String contractPath) throws Exception {
        final Map<String, Map<String, String>> contracts = getCompiledContract(contractPath);
        if (contracts == null) {
            getProject().getLogger().warn("\tNo Contract found for file '" + contractPath + "'");
            return;
        }
        for (final String contractName : contracts.keySet()) {
            try {
                getProject().getLogger().info("\tTry to build java class for contract '" + contractName + "'");
                generateJavaClass(contracts, contractName);
                getProject().getLogger().info("\tBuilt Class for contract '" + contractName + "'");
            } catch (final Exception e) {
                getProject().getLogger().error("Could not build java class for contract '" + contractName + "'", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> getCompiledContract(String contractPath)
            throws Exception {

        final File contractFile = new File(contractPath);
        if (!contractFile.exists() || contractFile.isDirectory()) {
            return Collections.emptyMap();
        }

        final String result = compileSolidityContract(contractFile)
                // TODO: for some reason a stdin is added to the contract name,
                // removing it the ugly way for now
                .replaceAll("<stdin>:", "");

        final Map<String, Object> json = (Map<String, Object>)
                OBJECT_MAPPER.readValue(result, Map.class);

        return (Map<String, Map<String, String>>) json.get("contracts");
    }

    private String compileSolidityContract(final File contractFile) throws Exception {

        final SolidityCompiler.Result result = SolidityCompiler.getInstance().compileSrc(
                contractFile,
                true,
                true,
                Options.ABI,
                Options.BIN,
                Options.INTERFACE,
                Options.METADATA
        );
        if (result.isFailed()) {
            throw new Exception("Could not compile solidity files: " + result.errors);
        }

        return result.output;
    }

    private void generateJavaClass(
            final Map<String, Map<String, String>> result,
            final String contractName) throws IOException, ClassNotFoundException {

        new SolidityFunctionWrapper(NATIVE_JAVA_TYPE).generateJavaFiles(
                contractName.split(":")[1],
                result.get(contractName).get("bin"),
                result.get(contractName).get("abi"),
                getOutputs().getFiles().getSingleFile().getAbsolutePath(),
                generatedJavaPackageName);
    }

    // Getters and setters
    public String getGeneratedJavaPackageName() {
        return generatedJavaPackageName;
    }

    public void setGeneratedJavaPackageName(final String generatedJavaPackageName) {
        this.generatedJavaPackageName = generatedJavaPackageName;
    }

}

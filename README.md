# OpenNTF LangChain4j for Domino

[LangChain4j](https://docs.langchain4j.dev) is a Java-native library that integrates concepts from popular LLM frameworks such as LangChain, Haystack, and LlamaIndex. Its purpose is to simplify the integration of large language models into Java applications using familiar Java idioms.

LangChain4j Domino extends this functionality to HCL Domino environments. It enables seamless use of LangChain4j within Domino applications and introduces document loaders for accessing Domino data as part of LangChain4j’s LLM workflows.

## Features

- Server and Designer plugins for HCL Domino 14+
- Support for standalone Java applications 
- Utilises Domino JNX
- Adds document loaders for Domino documents across various scenarios
- Demo code available at: [https://github.com/sbasegmez/LLM-Demos](https://github.com/sbasegmez/LLM-Demos)
- See the [Wiki](https://github.com/sbasegmez/langchain4j-domino/wiki) for details on supported models, vector databases, and other modules

## Core Library

The [langchain4j-domino](core-libs/langchain4j-domino) library leverages the Domino JNX API to provide Domino implementations for DocumentSource and Document Loaders.

Refer to the following classes for more information:

- [DominoAttachmentDocumentSource.java](core-libs/langchain4j-domino/src/main/java/org/openntf/langchain4j/data/DominoAttachmentDocumentSource.java) 
- [DominoDataDocumentSource.java](core-libs/langchain4j-domino/src/main/java/org/openntf/langchain4j/data/DominoDataDocumentSource.java)
- [DominoDocumentLoader.java](core-libs/langchain4j-domino/src/main/java/org/openntf/langchain4j/data/DominoDocumentLoader.java)
- [MetadataDefinition.java](core-libs/langchain4j-domino/src/main/java/org/openntf/langchain4j/data/MetadataDefinition.java)

## XSP Plugin

The [org.openntf.langchain4j.xsp](extension-plugins/org.openntf.langchain4j.xsp) plugin provides the necessary bindings for using the core library in XPages applications. It should be installed on both Domino Servers and Designer clients.

## Future Plans

- Domino port for Observability
- Beans for XPages
- Configuration management
- Logging enhancements
- Support for Java Agents and DOTS

## Known Issues

- A source bundle for Domino Designer is not available yet.

## How to Contribute

Please submit feature requests and bug reports via [GitHub Issues](https://github.com/sbasegmez/langchain4j-domino/issues).

Join the [OpenNTF Discord Channel](https://openntf.org/discord) for feedback and discussions.

## License

This project is licensed under the Apache License 2.0.

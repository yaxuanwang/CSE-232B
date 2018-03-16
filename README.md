# CSE 232B Database System Implementation
An XQuery processor based on ANTLR 4.7.1 on Java.

# Project Description
Our class project is the construction of an XQuery processor. We consider a subset/modification of XML’s data model, XQuery, and XQuery’s type system as described in this note. The processor receives an XQuery, parses it into an abstract tree representation, optimizes it and finally executes the optimized plan.

*Milestone 1 (Naïve Evaluation): A straightforward query execution engine receives the simplified XQuery and an input XML file and evaluates the query using a recursive evaluation routine which, given an XQuery expression (path, concatenation, element creation, etc) and a list of input nodes, produces a list of output nodes. For the XQuery parser, we recommend the jjtree tool provided with the javacc (Java Compiler Compiler) software, available for download here. Provided with a grammar, jjtree generates a compiler which automatically constructs abstract syntax trees of its input expressions.

*Milestone 2 (Efficient Evaluation): Implement a join operator as defined in Section 7 of this note. Implement an algorithm which detects the fact that the FOR and WHERE clause computation can be implemented using the join operator. You may assume that the input XQueries to be optimized are in the simplified "Core" syntax given in the note. No need to first normalize your queries to this form.

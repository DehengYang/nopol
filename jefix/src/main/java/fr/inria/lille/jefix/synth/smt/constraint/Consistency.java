package fr.inria.lille.jefix.synth.smt.constraint;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.smtlib.ICommand;
import org.smtlib.IExpr;
import org.smtlib.IExpr.IDeclaration;
import org.smtlib.IExpr.IQualifiedIdentifier;
import org.smtlib.IExpr.ISymbol;
import org.smtlib.ISort;
import org.smtlib.SMT.Configuration;

final class Consistency {

	static final String FUNCTION_NAME = "cons";
	private static final String OUTPUT_LINE_PREFIX = "LO_";

	private final ICommand.IFactory commandFactory;
	private final IQualifiedIdentifier distinct;
	private final IExpr.IFactory efactory;
	private final ISort.IFactory sortfactory;
	private final ISort intSort;

	Consistency(@Nonnull final Configuration smtConfig) {
		this.efactory = smtConfig.exprFactory;
		this.sortfactory = smtConfig.sortFactory;
		this.commandFactory = smtConfig.commandFactory;
		this.intSort = this.sortfactory.createSortExpression(this.efactory.symbol("Int"));
		this.distinct = this.efactory.symbol("distinct");
	}

	private IExpr createConstraint(final List<ISymbol> variables) {
		List<IExpr> constraints = new ArrayList<>(2 * variables.size());
		int i = 1;
		int size = variables.size();
		for (ISymbol leftOperand : variables) {
			Iterable<ISymbol> subList = variables.subList(i++, size);
			for (ISymbol rightOperand : subList) {
				constraints.add(this.efactory.fcn(this.distinct, leftOperand, rightOperand));
			}
		}
		return new Simplifier(this.efactory).simplifyAnd(constraints);
	}

	ICommand createFunctionDefinitionFor(@Nonnull final int operators) {
		List<IDeclaration> parameters = new ArrayList<>(operators);
		List<ISymbol> variables = new ArrayList<>(operators);
		for (int i = 0; i < operators; i++) {
			ISymbol symbol = this.efactory.symbol(OUTPUT_LINE_PREFIX + i);
			variables.add(symbol);
			parameters.add(this.efactory.declaration(symbol, this.intSort));
		}
		return this.commandFactory.define_fun(this.efactory.symbol(FUNCTION_NAME), parameters, this.sortfactory.Bool(),
				this.createConstraint(variables));
	}
}

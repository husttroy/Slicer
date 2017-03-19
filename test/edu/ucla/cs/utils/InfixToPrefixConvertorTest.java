package edu.ucla.cs.utils;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.Test;

public class InfixToPrefixConvertorTest {
	@Test
	public void testTokenize() {
		ArrayList<String> rel = InfixToPrefixConvertor
				.tokenize("!( 1+ 2 > 3*4 && 1 <= 2 || (a > b))");
		assertEquals(
				"[!, (, 1, +, 2, >, 3, *, 4, &&, 1, <=, 2, ||, (, a, >, b, ), )]",
				rel.toString());
	}

	@Test
	public void testPrefixer() {
		String infix = "! (( (1 +a0 *3)!= 0 )&&true && b0)";
		assertEquals("(! (&& (&& (! (== (+ 1 (* a0 3)) 0)) true) b0))",
				InfixToPrefixConvertor.infixToPrefixConvert(infix));
	}

	@Test
	public void testEndsWithVar() {
		String infix = "true && i0 >= 1 && !b0";
		assertEquals("(&& (&& true (>= i0 1)) (! b0))",
				InfixToPrefixConvertor.infixToPrefixConvert(infix));
	}
	
	@Test
	public void testConvertNegativeSign() {
		String simpleInfix = "i0==-1";
		assertEquals("(== i0 -1)", InfixToPrefixConvertor.infixToPrefixConvert(simpleInfix));
		String complexInfix = "!b0 && true && !(i0==-1) && !b2";
		assertEquals("(&& (&& (&& (! b0) true) (! (== i0 -1))) (! b2))", InfixToPrefixConvertor.infixToPrefixConvert(complexInfix));
		String minusSign = " (a - b) - c";
		assertEquals("(- (- a b) c)", InfixToPrefixConvertor.infixToPrefixConvert(minusSign));
	}
	
	@Test
	public void testConvertNegativeSign2() {
		String simpleInfix = "i0 < -1";
		assertEquals("(< i0 -1)", InfixToPrefixConvertor.infixToPrefixConvert(simpleInfix));
	}
}

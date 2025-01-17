
package com.mastercard.test.flow.validation;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.mastercard.test.flow.Flow;
import com.mastercard.test.flow.Metadata;
import com.mastercard.test.flow.Model;
import com.mastercard.test.flow.validation.InheritanceHealth.Phase;

/**
 * Exercises {@link InheritanceHealth}
 */
@SuppressWarnings("static-method")
class InheritanceHealthTest {

	/**
	 * Exercises our string diff metric
	 */
	@Test
	void diffDistance() {
		BiConsumer<String, Integer> test = ( in, out ) -> {
			String[] parts = in.split( ">" );
			assertEquals( out, InheritanceHealth.diffDistance(
					parts[0].replaceAll( "", "\n" ),
					parts[1].replaceAll( "", "\n" ) ),
					"for " + in );
		};

		test.accept( "a>a", 0 );
		test.accept( "abc>abc", 0 );

		test.accept( "a>b", 1 );
		test.accept( "abc>axc", 1 );
		test.accept( "abc>def", 3 );

		// unfortunate edge case: the diff lib would prefer to change 'sl' to 'r' and
		// then insert 'o' (totals to 3) than to change 'sl' to 'ro' (which would total
		// to 2)
		test.accept( "slot>root", 3 );
	}

	/**
	 * Exercising edge case where there are no root {@link Flow}s
	 */
	@Test
	void ourobouros() {
		InheritanceHealth ih = new InheritanceHealth( 0, 1, 1, Assertions::assertEquals );
		Model mdl = mdl( "ourobouros" );
		IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
				() -> ih.expect( mdl, "" ) );
		assertEquals( "No root flows?", iae.getMessage() );
	}

	/**
	 * Lack of {@link Flow}s is tolerated
	 */
	@Test
	void empty() {
		InheritanceHealth ih = new InheritanceHealth( 0, 20, 10, Assertions::assertEquals );
		Model mdl = mdl();

		ih.expect( mdl, "empty model" );

		AssertionError ae = assertThrows( AssertionError.class, () -> ih.expect( mdl, "wrong!" ) );
		assertEquals( "expected: <\"wrong!\"> but was: <\"empty model\">", ae.getMessage() );
	}

	/**
	 * A single {@link Flow}
	 */
	@Test
	void lonesome() {
		InheritanceHealth ih = new InheritanceHealth( 0, 20, 10, Assertions::assertEquals );
		Model mdl = mdl( "root" );
		ih.expect( mdl,
				"Actual            | Optimal          ",
				"roots          12 | roots          12",
				"edges           0 | edges           0",
				"total          12 | total          12",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%" );

		assertThrows( AssertionError.class, () -> ih.expect( mdl, "wrong!" ) );
	}

	/**
	 * Two {@link Flow}s
	 */
	@Test
	void pair() {
		new InheritanceHealth( 0, 20, 10, Assertions::assertEquals )
				.expect( mdl( "root", "roof" ),
						"Actual            | Optimal          ",
						"roots          24 | roots          12",
						"edges           0 | edges           1",
						"total          24 | total          13",
						"        0   0.00% |         1 100.00%",
						"        0   0.00% |         0   0.00%",
						"        0   0.00% |         0   0.00%",
						"        0   0.00% |         0   0.00%",
						"        0   0.00% |         0   0.00%",
						"        0   0.00% |         0   0.00%",
						"        0   0.00% |         0   0.00%",
						"        0   0.00% |         0   0.00%",
						"        0   0.00% |         0   0.00%",
						"        0   0.00% |         0   0.00%" );
	}

	/**
	 * Optimising a badly-formed chain. Going from:
	 *
	 * <pre>
	 * root
	 * └slap     4
	 *  └soot    3
	 *   └slat   2
	 *    └slot  1
	 *     └root 3
	 * </pre>
	 *
	 * to
	 *
	 * <pre>
	 * root
	 * ├root     0
	 * └soot     1
	 *  └slat    2
	 *   ├slap   1
	 *   └slot   1
	 * </pre>
	 *
	 * The optimised form is slightly unexpected, we'd have been better with:
	 *
	 * <pre>
	 * root
	 * ├root     0
	 * └soot     1
	 *  └slot    1
	 *   └slat   1
	 *    └slap  1
	 * </pre>
	 *
	 * This is an issue in the diff lib (see the last case in
	 * {@link #diffDistance()}), not much we can do about it.
	 */
	@Test
	void optimise() {
		new InheritanceHealth( 0, 4, 5, Assertions::assertEquals )
				.expect( mdl( "root", "root>slap", "slap>soot", "soot>slat", "slat>slot", "slot>root" ),
						"Actual            | Optimal          ",
						"roots          12 | roots          12",
						"edges          13 | edges           5",
						"total          25 | total          17",
						"        0   0.00% |         1  20.00%",
						"        1  20.00% |         3  60.00%",
						"        1  20.00% |         1  20.00%",
						"        2  40.00% |         0   0.00%",
						"        1  20.00% |         0   0.00%" );
	}

	/**
	 * Shows that flows that are not presented in the model, but that do exist in
	 * the inheritance hierarchy, are considered
	 */
	@Test
	void occultedFlows() {
		Model model = mdl( "root", "root>toot!", "toot>tort", "toot>tool" );

		assertEquals( "tool,tort,root", model.flows()
				.map( f -> f.meta().description().replaceAll( "\\s", "" ) )
				.collect( joining( "," ) ),
				"only three flows presented in the model" );

		// but 4 are considered in the inheritance health (1 root and three children)
		new InheritanceHealth( 0, 2, 3, Assertions::assertEquals )
				.expect( model,
						"Actual            | Optimal          ",
						"roots          12 | roots          12",
						"edges           3 | edges           3",
						"total          15 | total          15",
						"        0   0.00% |         0   0.00%",
						"        3 100.00% |         3 100.00%",
						"        0   0.00% |         0   0.00%" );
	}

	/**
	 * Demonstrating the listener interface
	 */
	@Test
	void listener() {
		List<String> events = new ArrayList<>();
		Map<String, Integer> flowIncidence = new TreeMap<>();

		InheritanceHealth ih = new InheritanceHealth( 0, 4, 5, Assertions::assertEquals );
		for( Phase p : Phase.values() ) {
			InheritanceHealth r = ih.progress( p,
					( flow, progress ) -> {
						events.add( String.format( "%s %.2f", p, progress ) );
						flowIncidence.compute( flow.toString(), ( k, v ) -> v == null ? 1 : v + 1 );
					} );
			assertSame( ih, r );
		}
		ih.expect( mdl( "root", "root>slap", "slap>soot", "soot>slat", "slat>slot" ),
				"Actual            | Optimal          ",
				"roots          12 | roots          12",
				"edges          10 | edges           5",
				"total          22 | total          17",
				"        0   0.00% |         0   0.00%",
				"        1  25.00% |         3  75.00%",
				"        1  25.00% |         1  25.00%",
				"        1  25.00% |         0   0.00%",
				"        1  25.00% |         0   0.00%" );

		assertEquals( ""
				+ "BUILD -1.00\n"
				+ "BUILD -1.00\n"
				+ "BUILD -1.00\n"
				+ "BUILD -1.00\n"
				+ "BUILD -1.00\n"
				+ "ACTUAL_COST 0.20\n"
				+ "ACTUAL_COST 0.40\n"
				+ "ACTUAL_COST 0.60\n"
				+ "ACTUAL_COST 0.80\n"
				+ "ACTUAL_COST 1.00\n"
				+ "OPTIMISE 0.20\n"
				+ "OPTIMISE 0.40\n"
				+ "OPTIMISE 0.60\n"
				+ "OPTIMISE 0.80\n"
				+ "OPTIMISE 1.00\n"
				+ "OPTIMAL_COST 0.20\n"
				+ "OPTIMAL_COST 0.40\n"
				+ "OPTIMAL_COST 0.60\n"
				+ "OPTIMAL_COST 0.80\n"
				+ "OPTIMAL_COST 1.00",
				events.stream().collect( joining( "\n" ) ) );
		assertEquals( ""
				+ "root = 4\n"
				+ "slap = 4\n"
				+ "slat = 4\n"
				+ "slot = 4\n"
				+ "soot = 4",
				flowIncidence.entrySet().stream()
						.map( e -> e.getKey() + " = " + e.getValue() )
						.collect( joining( "\n" ) ) );
	}

	/**
	 * The height of the plots is limited by the value range
	 */
	@Test
	void height() {
		new InheritanceHealth( 2, 6, 10, Assertions::assertEquals )
				.expect( mdl( "root", "sppu" ),
						"Actual            | Optimal          ",
						"roots          24 | roots          12",
						"edges           0 | edges           4",
						"total          24 | total          16",
						"        0   0.00% |         0   0.00%",
						"        0   0.00% |         0   0.00%",
						"        0   0.00% |         1 100.00%",
						"        0   0.00% |         0   0.00%",
						"        0   0.00% |         0   0.00%" );
	}

	/**
	 * Demonstrates what happens when the plot range does not fit the data
	 */
	@Test
	void plotRange() {
		Model mdl = mdl( "root", "sppu" );
		{
			InheritanceHealth fit = new InheritanceHealth( 4, 4, 5, Assertions::assertEquals );
			fit.expect( mdl,
					"Actual            | Optimal          ",
					"roots          24 | roots          12",
					"edges           0 | edges           4",
					"total          24 | total          16",
					"        0   0.00% |         1 100.00%" );
		}
		{
			InheritanceHealth low = new InheritanceHealth( 3, 3, 5, Assertions::assertEquals );
			IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
					() -> low.expect( mdl, "" ) );
			assertEquals( ""
					+ "Maximum edge weight 4 higher than plot range maximum 3.\n"
					+ "Increase the plot range maximum to at least 4 and try again.",
					iae.getMessage() );
		}
		{
			InheritanceHealth high = new InheritanceHealth( 5, 5, 5, Assertions::assertEquals );
			IllegalArgumentException iae = assertThrows( IllegalArgumentException.class,
					() -> high.expect( mdl, "" ) );
			assertEquals( ""
					+ "Minimum edge weight 4 lower than plot range minimum 5.\n"
					+ "Decrease the plot range minimum to at most 4 and try again.",
					iae.getMessage() );
		}
	}

	/**
	 * Shows that the cost-estimation behaviour can be altered. Here the creation
	 * cost is just the numeric value of the flow description, and the derivation
	 * cost is the numeric difference. Optimisation takes us from:
	 *
	 * <pre>
	* 1
	* └16     15
	*  └2     14
	*   └8     6
	*    └4    4
	 * </pre>
	 *
	 * to:
	 *
	 * <pre>
	 * 1
	 * └2      1
	 *  └4     2
	 *   └8    4
	 *    └16  8
	 * </pre>
	 */
	@Test
	void customCost() {
		Model mdl = mdl( "1", "1>16", "16>2", "2>8", "8>4" );

		ToIntFunction<Flow> value = f -> Integer
				.parseInt( f.meta().description().replaceAll( "\\D", "" ) );

		InheritanceHealth ih = new InheritanceHealth( 0, 15, 16, Assertions::assertEquals );
		assertSame( ih, ih.creationCost( value ) );
		assertSame( ih, ih.derivationCost( ( p, c ) -> Math.abs(
				value.applyAsInt( p ) - value.applyAsInt( c ) ) ) );

		ih.expect( mdl,
				"Actual            | Optimal          ",
				"roots           1 | roots           1",
				"edges          39 | edges          15",
				"total          40 | total          16",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         1  25.00%",
				"        0   0.00% |         1  25.00%",
				"        0   0.00% |         0   0.00%",
				"        1  25.00% |         1  25.00%",
				"        0   0.00% |         0   0.00%",
				"        1  25.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         1  25.00%",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%",
				"        0   0.00% |         0   0.00%",
				"        1  25.00% |         0   0.00%",
				"        1  25.00% |         0   0.00%" );
	}

	private static final Pattern CHILD = Pattern.compile( "(.*)>(.*)" );

	private static Model mdl( String... flowDefs ) {
		Map<String, Flow> ids = new HashMap<>();

		List<Flow> flows = new ArrayList<>();
		for( String def : flowDefs ) {
			Flow basis = null;
			String desc = def;
			Matcher cm = CHILD.matcher( desc );
			if( cm.matches() ) {
				basis = Optional.of( ids )
						.map( m -> m.get( cm.group( 1 ) ) )
						.orElseThrow( () -> new IllegalArgumentException( "No basis " + cm.group( 1 ) ) );
				desc = cm.group( 2 );
			}
			boolean occulted = desc.contains( "!" );
			desc = desc.replaceAll( "[^a-z0-9]", "" );
			Metadata meta = mock( Metadata.class );
			when( meta.description() ).thenReturn( desc.replaceAll( "", "\n" ) );

			Flow flow = mock( Flow.class );

			if( "ourobouros".equals( desc ) ) {
				basis = flow;
			}

			when( flow.meta() ).thenReturn( meta );
			when( flow.basis() ).thenReturn( basis );
			when( flow.toString() ).thenReturn( desc );

			ids.put( desc, flow );

			if( !occulted ) {
				flows.add( flow );
			}
		}

		// let's not have a root flow always coming first
		Collections.reverse( flows );

		Model mdl = mock( Model.class );
		when( mdl.flows() ).thenAnswer( a -> flows.stream() );
		return mdl;
	}
}

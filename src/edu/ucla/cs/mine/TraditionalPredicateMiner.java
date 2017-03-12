package edu.ucla.cs.mine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class TraditionalPredicateMiner extends PredicatePatternMiner{
	static final Pattern METHOD_CALL = Pattern.compile("((new )?[a-zA-Z0-9_]+)\\(((.+),)*\\)");

	final String path = "/home/troy/research/BOA/Maple/example/new_sequence.txt";
	final String sequence_path = "/home/troy/research/BOA/Maple/example/new_output.txt";
	
	public TraditionalPredicateMiner(ArrayList<String> pattern) {
		super(pattern);
	}

	@Override
	protected void loadAndFilterPredicate() {
		// find API call sequences that follow the pattern
		SequencePatternVerifier pv = new SequencePatternVerifier(pattern);
		pv.verify(sequence_path);
		
		// read the full output of the traditional analysis
		File output = new File(path);
		try (BufferedReader br = new BufferedReader(new FileReader(output))) {
			String line;
			while ((line = br.readLine()) != null) {
				String key = line.substring(line.indexOf("[") + 1, line.indexOf("]"));
				key = key.replaceAll("\\!", " ** ");
				if(pv.support.containsKey(key)) {
					// this sequence follows the pattern
					String seq = line.substring(line.indexOf("] =") + 3).trim();
					String[] arr = seq.split("->");
					
					HashMap<String, ArrayList<String>> map = new HashMap<String, ArrayList<String>>();
					// skip the first element because it is empty string
					for(int i = 1; i < arr.length; i++){
						String expr;
						if (arr[i].contains("}")){
							expr = arr[i].split("}")[0];
						}else{
							expr = arr[i];
						}
						
						// skip if it is a control-flow construct
						String s = expr.trim();
						if(s.equals("IF {") || s.equals("ELSE {") || s.equals("TRY {") || s.equals("CATCH {") || s.equals("LOOP {") || s.equals("FINALLY {")) {
							continue;
						}
						
						// trim expression
						expr = expr.trim();
						
						// split by @
						String[] ss = expr.split("@");
						String predicate = null;
						String item = null;
						// we assume the last @ splits the statement and its precondition
						// but we will run into trouble if there is an @ in the precondition
						if(ss.length == 1) {
							item = ss[0];
							predicate = "true";
						} else {
							predicate = ss[ss.length - 1];
							for (int j = 0; j < ss.length - 1; j++) {
								// concatenate the method call expression if it contains @ and is split
								if(item == null) {
									item = ss[j];
								} else {
									item += ss[j];
								}
							}
						}
						
						// pre-check to avoid unnecessary pattern matching for the performance purpose
						if(!item.contains("(") || !item.contains(")")) {
							continue;
						}
						
						// extract predicates for each method call in this expression
						HashMap<String, ArrayList<String>> predicates = propagatePredicates(item.trim(), predicate);
						
						// aggregate the predicates with those from previous sequences
						for(String name : predicates.keySet()) {
							if(map.containsKey(name)) {
								map.get(name).addAll(predicates.get(name));
							} else {
								map.put(name, predicates.get(name));
							}
						}
					}
					
					this.predicates.put(key, map);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public HashMap<String, ArrayList<String>> propagatePredicates(String expr, String predicate) {
		HashMap<String, ArrayList<String>> predicates = new HashMap<String, ArrayList<String>>();
		Matcher m = METHOD_CALL.matcher(expr);
		while(m.find()) {
			String apiName = m.group(1);
			if (!pattern.contains(apiName)) {
				// skip if the API call does not appear in the sequence pattern
				continue;
			}
			
			String receiver = null;
			if(!apiName.startsWith("new ")) {
				// make sure this is not a call to class constructor since class constructors do not have receivers
				int index = expr.indexOf(apiName);
				String sub = expr.substring(0, index);
				if(sub.endsWith(".")) {
					// make sure it is not a call to local method since local method calls also do not have receivers
					if(sub.startsWith("return ")) {
						// return statement
						receiver = sub.substring(7, sub.length() - 1);
					} else if (sub.matches("[a-zA-Z0-9_]+=.+")) {
						// assignment statement
						receiver = sub.substring(sub.indexOf("=") + 1, sub.length() - 1);
					} else {
						// regular method call
						receiver = sub.substring(0, sub.length() - 1);
					}
				}
			}
			
			// extract the arguments 
			String args = m.group(3);
			ArrayList<String> arguments = new ArrayList<String>();
			if(args != null) {
				// split arguments
				// this has to be done here because we do not want previous argument to be part of the receiver of API calls in the next argument
				arguments = getArguments(args);
				for(String arg : arguments) {
					HashMap<String, ArrayList<String>> p2 = propagatePredicates(arg, predicate);
					// aggregate
					for(String name : p2.keySet()) {
						if(predicates.containsKey(name)) {
							predicates.get(name).addAll(p2.get(name));
						} else {
							predicates.put(name, p2.get(name));
						}
					}
				}
			}
			
			HashSet<String> relevant_elements = new HashSet<String>();
			if(receiver != null) {
				relevant_elements.add(receiver);
			}
			relevant_elements.addAll(arguments);
			
			// remove irrelevant clauses
			String conditioned_predicate = condition(relevant_elements, predicate);
			
			// normalize names
			// declare temporary variables to fit the API
			ArrayList<String> temp1 = new ArrayList<String>();
			if (receiver != null) {
				temp1.add(receiver);
			}
			ArrayList<ArrayList<String>> temp2 = new ArrayList<ArrayList<String>>();
			temp2.add(arguments);
			
			String normalized_predicate = normalize(conditioned_predicate, temp1, temp2);
	
			ArrayList<String> value;
			if(predicates.containsKey(apiName)) {
				value = predicates.get(apiName);
			}else{
				value = new ArrayList<String>();
			}
			value.add(normalized_predicate);
			predicates.put(apiName, value);
		}
		
		return predicates;
	}
	
	public ArrayList<String> getArguments(String args) {
		ArrayList<String> list = new ArrayList<String>();
		int stack = 0;
		String[] ss = args.split(",");
		String arg = "";
		for (String s : ss) {
			int open = StringUtils.countMatches(s, "(");
			int close = StringUtils.countMatches(s, ")");
			stack += open - close;
			if(stack != 0) {
				arg += s + ",";
			} else {
				arg += s;
				list.add(arg);
				arg = "";
			}
		}
		return list;
	}
	
	public static void main(String[] args) {
		ArrayList<String> pattern = new ArrayList<String>();
		pattern.add("IF {");
		pattern.add("createNewFile");
		pattern.add("}");
		TraditionalPredicateMiner pm = new TraditionalPredicateMiner(pattern);
		pm.process();
	}
}
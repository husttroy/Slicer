package edu.ucla.cs.evaluate.manual;

import java.util.ArrayList;
import java.util.HashSet;

import edu.ucla.cs.main.AnomalyDetection;
import edu.ucla.cs.model.APICall;
import edu.ucla.cs.model.APISeqItem;
import edu.ucla.cs.model.ControlConstruct;

public class TypeArray {
	public static void main(String[] args) {
		ArrayList<APISeqItem> pattern1 = new ArrayList<APISeqItem>();
		pattern1.add(ControlConstruct.TRY);
		pattern1.add(new APICall("getString", "true"));
		pattern1.add(ControlConstruct.END_BLOCK);
		pattern1.add(ControlConstruct.CATCH);
		pattern1.add(ControlConstruct.END_BLOCK);
		
		HashSet<ArrayList<APISeqItem>> patterns = new HashSet<ArrayList<APISeqItem>>();
		patterns.add(pattern1);
		
		HashSet<String> types = new HashSet<String>();
		types.add("TypedArray");
		HashSet<ArrayList<String>> queries = new HashSet<ArrayList<String>>();
		ArrayList<String> apis = new ArrayList<String>();
		apis.add("getString");
		queries.add(apis);
		
		AnomalyDetection detect = new AnomalyDetection(types, queries, patterns);
		detect.run();
	}
}

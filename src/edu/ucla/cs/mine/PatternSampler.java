package edu.ucla.cs.mine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.ucla.cs.model.APICall;
import edu.ucla.cs.model.APISeqItem;
import edu.ucla.cs.utils.SAT;
import edu.ucla.cs.utils.SubsequenceCounter;

public class PatternSampler {
	public String seqFile;
	public String orgFile;
	public HashMap<String, ArrayList<String>> seqs;

	public PatternSampler(String seqFile, String orgFile) {
		this.seqFile = seqFile;
		this.orgFile = orgFile;
		seqs = PatternUtils.readAPISequences(seqFile);
	}

	public ArrayList<String> sample(ArrayList<APISeqItem> pattern, int n) {

		// extract the API call sequence only without guard conditions
		ArrayList<String> patternS = PatternUtils
				.extractSequenceWithoutGuardAndArgCount(pattern);
		SequencePatternVerifier verifyS = new SequencePatternVerifier(patternS);
		verifyS.verify(seqFile);

		TraditionalPredicateMiner minerP = new TraditionalPredicateMiner(
				patternS, orgFile, seqFile);
		minerP.loadAndFilterPredicate();
		
		// rank code examples that support the sequence ordering based on their
		// conciseness (i.e., number of API calls and control constructs in a
		// sequence) and their ambiguity (i.e., whether there is an n-n mapping
		// between items in the pattern and items in the sequence)
		List<Map.Entry<String, ArrayList<String>>> sortedList = new LinkedList<Map.Entry<String, ArrayList<String>>>(verifyS.support.entrySet());
		
		Collections.sort(sortedList, new Comparator<Map.Entry<String, ArrayList<String>>>() {

			@Override
			public int compare(Entry<String, ArrayList<String>> o1,
					Entry<String, ArrayList<String>> o2) {
				// first we prefer non-ambiguous examples
				ArrayList<String> seq1 = o1.getValue();
				ArrayList<String> seq2 = o2.getValue();
				if (!isAmbiguous(patternS, seq1) && isAmbiguous(patternS, seq2)) {
					return -1;
				} else if (isAmbiguous(patternS, seq1) && !isAmbiguous(patternS, seq2)) {
					return 1;
				}
				
				if (seq1.size() < seq2.size()) {
					return -1;
				} else if (seq1.size() > seq2.size()) {
					return 1;
				} else {
					return 0;
				}
			}

			private boolean isAmbiguous(ArrayList<String> pattern,
					ArrayList<String> seq) {
				// make a copy of seq
				ArrayList<String> copy = new ArrayList<String>(seq);
				
				// strip all items in value that are not in pattern to reduce complexity
				for(int i = copy.size() -1; i > 0; i--) {
					if(!pattern.contains(copy.get(i))) {
						copy.remove(i);
					}
				}
				
				SubsequenceCounter counter = new SubsequenceCounter(copy, pattern);
				return counter.countMatches() > 1;
			}
			
		});

		// get ids of all code examples that respect the sequence ordering and the guard condition
		ArrayList<String> sampleIDs = new ArrayList<String>();
		SAT sat = new SAT();
		for (Map.Entry<String, ArrayList<String>> entry : sortedList) {
			String id = entry.getKey();
			HashMap<String, ArrayList<String>> map = minerP.predicates.get(id);
			if(map == null)  continue;
			boolean flag = true;
			for (APISeqItem item : pattern) {
				if (!flag) {
					break;
				}
				if (item instanceof APICall) {
					APICall call = (APICall) item;
					if (call.condition.equals("true")) {
						continue;
					} else {
						ArrayList<String> predicates = map.get(call.name);
						if (predicates == null) {
							flag = false;
							break;
						}
						for (String predicate : predicates) {
							if (!sat.checkImplication(predicate, call.condition)) {
								flag = false;
								break;
							}
						}
					}
				}
			}

			if (flag) {
				sampleIDs.add(id);
				if(sampleIDs.size() == n) {
					break;
				}
			}
		}

		// read the original file again to get the complete sequence of each
		// sample
		ArrayList<String> sample = new ArrayList<String>();
		for(int i = 0; i < n; i ++) {
			// initialize the list with null so that we can insert an element to anywhere we want without IndexOutOfBounds error 
			sample.add(null);
		}
		File output = new File(orgFile);
		try (BufferedReader br = new BufferedReader(new FileReader(output))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (!line.startsWith("results[")) {
					continue;
				}
				String key = line.substring(line.indexOf("[") + 1,
						line.indexOf("][SEQ]"));
				key = key.replaceAll("\\!", " ** ");
				if (sampleIDs.contains(key)) {
					sample.add(sampleIDs.indexOf(key), line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// post-process, remove all nulls from the sample list
		for(int i = sample.size() - 1; i > -1; i--) {
			if(sample.get(i) == null) {
				sample.remove(i);
			}
		}

		return sample;
	}
}

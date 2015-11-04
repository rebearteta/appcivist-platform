package delegates;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Map;
import java.io.FileReader;
import java.util.HashMap;
import java.io.BufferedReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.io.IOException;

import delegates.ContributionsDelegate;
import models.Contribution;
import models.Assembly;
import models.transfer.AssemblySummaryTransfer;

import org.dozer.DozerBeanMapper;

import play.Play;



// @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
// @DiscriminatorColumn(name = "TYPE")
@JsonInclude(Include.NON_EMPTY)
public class RedundanciesDelegate {

	public static DozerBeanMapper mapper;
	static {
		List<String> mappingFiles = Play.application().configuration()
				.getStringList("appcivist.dozer.mappingFiles");
		mapper = new DozerBeanMapper(mappingFiles);
	}

	private Hashtable<String, ArrayList<Long>> contributionsTable = new Hashtable<String, ArrayList<Long>>();

	private Hashtable<String, ArrayList<Long>> similiarContriTable = new Hashtable<Long, ArrayList<Long>>();

	private Hashtable<Long, ArrayList<String>> keywordsTable = new Hashtable<Long, ArrayList<String>>();


	public ArrayList<String> get_keywordsList_byID(Long id) {
		return keywordsTable.get(id);
	}

	public Hashtable<Long, ArrayList<String>> getKeywordsTable() {
		return keywordsTable;
	}

	public Hashtable<Long, ArrayList<Long>> getSimiliarContriTable() {
		return similiarContriTable;
	}

	public Hashtable<String, ArrayList<Long>> getContributionsTable() {
		return contributionsTable;
	}


		/* returns a list of contributions by its contribution ID for an inputed keyword*/
	public ArrayList<Long> get(String keyword, Hashtable table) {
		return table.get(keyword);
	}

	/* adds a contribution by its ID to the hashtable based on the keyword */
	public void put(String keyword, Long contributionID, Hashtable table) {
		if (keyword == null || contributionID == null) {
            throw new IllegalArgumentException("null argument");
        }
        if (table.containsKey(keyword)) {
        	table.get(keyword).add(contributionID);
        } else {
        	ArrayList<Long> new_list = new ArrayList<Long>();
        	new_list.add(contributionID);
        	table.put(keyword, new_list);
        }
	}

	public void put_list (ArrayList<String> keywords, Long contributionID, Hashtable table) {
		for (int i =0; i < keywords.size(); i++) {
			table.put(keywords.get(i), contributionID);
	    }
	}




		/* reads the stopwords file and turn the file into an array of stopwords*/
	public static void get_stops(String filename) {

		ArrayList<String> stop_words = new ArrayList<String>(); 
		BufferedReader input;
		input = null;
		try {
			input = new BufferedReader (new FileReader(filename + ".txt"));
			String line;
			stop_words = new ArrayList<String>();
			while ((line = input.readLine()) != null) {
				line.replaceAll("\\s+","");
				stop_words.add(line);
			}
			return stop_words;
		} catch (IOException e) {
		    e.printStackTrace();
		} finally {
		    try {
		        input.close();
		    } catch (IOException e) {
		        e.printStackTrace();
		    }
		}
	}

	/* reads the text body of a contribution to remove all stop words (words that are not necessary/useless) */
	public static ArrayList<String> remove_stops(String contributionBody) {
		ArrayList<String> stop_words = get_stops("stop-words-english1");
		String removed_symbols = contributionBody.replaceAll("[;/$*,.!?&]", "");
		String[] text = removed_symbols.split(" ");
		cleared_words = new ArrayList<String>();
		for (int i = 0; i < text.length; i++) {
			if (!stop_words.contains(text[i])) {
				cleared_words.add(text[i]);
			}
		}
		return cleared_words;
	}

		public static ArrayList<String> get_important_keywords(ArrayList<String> cleared_text) {
		ArrayList<String> important_words = new ArrayList<String>();
		Boolean previous = false; 
		String previous_word;
		for (int i = 0; i < cleared_text.size(); i++) {
			String word = cleared_text.get(i);
			int last_index = important_words.size() - 1;
			if (isInteger(word) || word.charAt(0) == Character.toUpperCase(word.charAt(0))) {
				if (previous) {
					previous_word = important_words.get(last_index);
					if (previous_word.charAt(previous_word.length() - 1) == ' ') {
						previous_word += word;
						important_words.set(last_index, previous_word);
					} else {
						previous_word += " " + word;
						important_words.set(last_index, previous_word);
					}
				} else {
					previous = true;
					if (i != 0) {
						important_words.add(word);
						// getKeywordsTable().get(id).add(word); // this is newly added... may contain errors
						// getKeywordsTable.put(id, word);   //just added this line to add important words with 
					}
				}
			} else {
				previous = false; 
			}
		}
		return important_words;
	}








	public static ArrayList<String> find_keywords(Long contriID) {

		Contribution c = Contribution.read(contriID);
		String text_string = c.getText();
		ArrayList<String> cleared_text = remove_stops(text_string);

		// convert text string into an array of words

		Map<String, Integer> times_occurred = new HashMap<String, Integer>();
		for (int i = 0; i < cleared_text.size(); i++) {
			String word = cleared_text.get(i);
		   Integer oldCount = times_occurred.get(word);
		   if (oldCount == null) {
		   	 oldCount = 0;
		   } 
		   times_occurred.put(word, oldCount + 1);
		}
		/* Sorts the keywords by te number of repetitions, 
			then return a list of keywords with atleast 2 repetition) */
		Map<String, Integer> sortedMap = sortByComparator(times_occurred, false);
		ArrayList<String> keywords = new ArrayList<String>();
		for (Entry<String, Integer> entry : sortedMap.entrySet()) {
			if (entry.getValue() >= 2) {
				keywords.add(entry.getKey());
				// System.out.println(entry.getKey() + " " + entry.getValue());
			}
		}
		ArrayList<String> all_keywords = merge_lists(keywords, get_important_keywords(text_string));
		getKeywordsTable.put(contriID, all_keywords); /// this is newly added! <<<may contain errors
		return all_keywords;
	}



		/* Given a list of keywords from this.contribution, 
		search through the hashtable and find contributions that match most with this.contribution
		this is based on some type of heuristic/standard */
	public static ArrayList<Long> match_keywords(ArrayList<String> keywordsList, Long id) {
		Map<Long, Integer> matched_contributions = new HashMap<Long, Integer>();
		ArrayList<String> a = get_keywordsList_byID(id); // dont think i used this anywhere

		for (int i = 0; i < keywordsList.size(); i++) {
			int matched_counts = 0;
			String word = keywordsList.get(i);
			ArrayList<Long> id_List = _table.get(word);
			if (id_List != null) {
				for (int j = 0; j < id_List.size(); j++) {
					Long id = id_List.get(j);
					if (get_keywordsList_byID(id).contains(word)) {
			   			matched_counts += 1;
			   		} if (get_tags(id).contains(word)) {
			   			matched_counts += 1;
			   		} if (matched_counts >= 1) {
			   			if (matched_contributions.containsKey(id)) {
			   				int value = matched_contributions.get(id);
			   				matched_counts += value;
			   			}
			   			matched_contributions.put(id, matched_counts);
			   		}
				}
			}
		}
		ArrayList<Long> similiarContri = new ArrayList<Long>();
		Map<Long, Integer> sortedMap = longSortByComparator(matched_contributions, false);
		for (Entry<Long, Integer> entry : sortedMap.entrySet()) {
			similiarContri.add(entry.getKey());
		}

		similiarContriTable.put(id, similiarContri);  // this is also new! may contain errors
		return similiarContri;
	}














	public static boolean isInteger(String s) {
	    return isInteger(s,10);
	}
	public static boolean isInteger(String s, int radix) {
	    if(s.isEmpty()) return false;
	    for(int i = 0; i < s.length(); i++) {
	        if(i == 0 && s.charAt(i) == '-') {
	            if(s.length() == 1) return false;
	            else continue;
	        }
	        if(Character.digit(s.charAt(i),radix) < 0) return false;
	    }
	    return true;
	}



	/* sorts a Hashmap by it's values in ascending or descending order */
	private static Map<String, Integer> sortByComparator(Map<String, Integer> unsortMap, final boolean order) {
        List<Entry<String, Integer>> list = new LinkedList<Entry<String, Integer>>(unsortMap.entrySet());

        /* Sorting the list based on values */
        Collections.sort(list, new Comparator<Entry<String, Integer>>() {
            public int compare(Entry<String, Integer> o1,
                    Entry<String, Integer> o2) {
                if (order) {
                    return o1.getValue().compareTo(o2.getValue());
                } else {
                    return o2.getValue().compareTo(o1.getValue());
                }
            }
        });
        /* Maintaining insertion order with the help of LinkedList */
        Map<String, Integer> sortedMap = new LinkedHashMap<String, Integer>();
        for (Entry<String, Integer> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return sortedMap;
    }

    private static Map<Long, Integer> longSortByComparator(Map<Long, Integer> unsortMap, final boolean order) {
        List<Entry<Long, Integer>> list = new LinkedList<Entry<Long, Integer>>(unsortMap.entrySet());

        /* Sorting the list based on values */
        Collections.sort(list, new Comparator<Entry<Long, Integer>>() {
            public int compare(Entry<Long, Integer> o1,
                    Entry<Long, Integer> o2) {
                if (order) {
                    return o1.getValue().compareTo(o2.getValue());
                } else {
                    return o2.getValue().compareTo(o1.getValue());
                }
            }
        });
        /* Maintaining insertion order with the help of LinkedList */
        Map<Long, Integer> sortedMap = new LinkedHashMap<Long, Integer>();
        for (Entry<Long, Integer> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return sortedMap;
    }
	
    public static ArrayList<String> merge_lists(ArrayList<String> a, ArrayList<String> b) {
    	Set<String> set = new HashSet<String>(a);
		set.addAll(b);
		ArrayList<String> mergeList = new ArrayList<String>(set);
    	return mergeList;
    }











}



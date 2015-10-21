package cs224n.assignment;

import cs224n.assignment.Grammar.UnaryRule;
import cs224n.assignment.Grammar.BinaryRule;
import cs224n.ling.Tree;
import cs224n.util.CounterMap;
import cs224n.util.Triplet;

import java.util.*;

/**
 * The CKY PCFG Parser you will implement.
 */
public class Parser2 implements Parser {
	private Grammar grammar;
	private Lexicon lexicon;
	CounterMap<String, String> scores = new CounterMap<String, String>();
	HashMap<Triplet<Integer, Integer, String>, Triplet<Integer, String, String>> backs = new HashMap<Triplet<Integer, Integer, String>, Triplet<Integer, String, String>>();
	// Data structure
	private int[][] scoreIdx;
	// Single Dimenionized Array for Storing score for given begin/end
	private List<Map<String, Double>> scoreTable;
	// Single Dimenionzied Array for Storing the backtrack information for given
	// begin/end
	private List<Map<String, Triplet<Integer, String, String>>> backTable;

	// Training
	public void train(List<Tree<String>> trainTrees) {
		List<Tree<String>> binarizedTrees = new ArrayList<Tree<String>>();
		for (Tree<String> tree : trainTrees) {
			binarizedTrees.add(TreeAnnotations.annotateTree(tree));
		}
		lexicon = new Lexicon(binarizedTrees);
		grammar = new Grammar(binarizedTrees);
	}

	// Build tree
	private Tree<String> backtrackBuildTree(int begin, int end, String tag) {

		Map<String, Triplet<Integer, String, String>> back = backTable.get(scoreIdx[begin][end]);
		Triplet<Integer, String, String> triple = back.get(tag);

		// Leaf case
		if (triple == null) {
			return new Tree<String>(tag);
		}

		if (triple.getFirst() == -1) {
			// Unary case
			// Backtrack to build the sole children node.
			Tree<String> subtree = null;
			if (tag.equals(triple.getSecond()))
				subtree = new Tree<String>(triple.getSecond());
			else
				subtree = backtrackBuildTree(begin, end, triple.getSecond());
			Tree<String> ret = new Tree<String>(tag, Collections.singletonList(subtree));
			return ret;
		} else {
			// Binary case
			// Backtrack to build the children nodes.
			Tree<String> leftTree = backtrackBuildTree(begin, triple.getFirst(), triple.getSecond());
			Tree<String> rightTree = backtrackBuildTree(triple.getFirst(), end, triple.getThird());
			List<Tree<String>> child = new ArrayList<Tree<String>>();
			child.add(leftTree);
			child.add(rightTree);
			Tree<String> ret = new Tree<String>(tag, child);
			return ret;
		}
	}

	public Tree<String> getBestParse(List<String> sentence) {

		// Initialization
		int N = sentence.size() + 1;
		scoreIdx = new int[N][N];

		for (int i = 0; i < scoreIdx.length; ++i)
			for (int j = 0; j < scoreIdx[0].length; ++j)
				scoreIdx[i][j] = -1;

		scoreTable = new ArrayList<Map<String, Double>>(N * (N + 1) / 2);
		backTable = new ArrayList<Map<String, Triplet<Integer, String, String>>>(N * (N + 1) / 2);

		// Dynamic programming base case
		for (int i = 0; i < sentence.size(); ++i) {
			// Map<String, Double> score = new HashMap<String, Double>();
			Map<String, Triplet<Integer, String, String>> back = new HashMap<String, Triplet<Integer, String, String>>();
			String tmpPair = i + "+" + (i + 1);

			for (String tag : lexicon.getAllTags()) {
				scores.setCount(tmpPair, tag, lexicon.scoreTagging(sentence.get(i), tag));

				// score.put(tag, lexicon.scoreTagging(sentence.get(i), tag));
				back.put(tag, new Triplet<Integer, String, String>(-1, sentence.get(i), ""));
				backs.put(new Triplet<Integer, Integer, String>(i, i + 1, tag),
						new Triplet<Integer, String, String>(-1, "", sentence.get(i)));
			}

			// Handling unary rules, apply as much as rule until no unary rule
			// can be applied
			boolean added = true;
			while (added) {
				added = false;
				// Set<String> S = new HashSet<String>(score.keySet());
				for (String key : scores.getCounter(tmpPair).keySet()) {
					for (UnaryRule r : grammar.getUnaryRulesByChild(key)) {
						double prob = scores.getCount(tmpPair, key) * r.getScore();
						if (!scores.getCounter(tmpPair).containsKey(r.getParent())
								|| scores.getCount(tmpPair, r.getParent()) < prob) {
							scores.setCount(tmpPair, r.getParent(),
									lexicon.scoreTagging(sentence.get(i), r.getParent()));

							// score.put(r.getParent(), prob);
							back.put(r.getParent(), new Triplet<Integer, String, String>(-1, r.getChild(), ""));
							backs.put(new Triplet<Integer, Integer, String>(i, i + 1, r.getParent()),
									new Triplet<Integer, String, String>(-1, "", key));
							added = true;
						}
					}
				}
			}
			scoreIdx[i][i + 1] = scoreTable.size();
			// scoreTable.add(score);
			backTable.add(back);
		}

		// Dynamic programming
		for (int span = 2; span <= sentence.size(); ++span) {
			for (int begin = 0; begin <= sentence.size() - span; ++begin) {

				int end = begin + span;
				String A = begin + "+" + end;

				// Map<String, Double> score = new HashMap<String, Double>();
				Map<String, Triplet<Integer, String, String>> back = new HashMap<String, Triplet<Integer, String, String>>();

				// Binary rules
				for (int split = begin + 1; split <= end - 1; ++split) {
					String B = begin + "+" + split;
					String C = split + "+" + end;
					if (!(scoreIdx[begin][split] != -1 && scoreIdx[split][end] != -1)) {
						System.err.println("Dynamic programming exception");
					}

					// Map<String, Double> left =
					// scoreTable.get(scoreIdx[begin][split]);
					// Map<String, Double> right =
					// scoreTable.get(scoreIdx[split][end]);

					for (String leftKey : scores.getCounter(B).keySet()) {
						for (BinaryRule br : grammar.getBinaryRulesByLeftChild(leftKey)) {
							if (!scores.getCounter(C).containsKey(br.getRightChild()))
								continue;
							double prob = scores.getCount(B, br.getLeftChild()) * scores.getCount(C, br.getRightChild())
									* br.getScore();
							// if (!score.containsKey(br.getParent()) ||
							// scores.getCount(A, br.getParent()) < prob) {
							if (!scores.getCounter(A).containsKey(br.getParent())
									|| scores.getCount(A, br.getParent()) < prob) {

								// Filled the CKY table and track the split
								// information
								scores.setCount(A, br.getParent(), prob);

								// score.put(br.getParent(), prob);
								back.put(br.getParent(), new Triplet<Integer, String, String>(split, br.getLeftChild(),
										br.getRightChild()));
								backs.put(new Triplet<Integer, Integer, String>(begin, end, br.getParent()),
										new Triplet<Integer, String, String>(split, br.getLeftChild(),
												br.getRightChild()));
							}
						}
					}
				}

				// Unary rules
				boolean added = true;
				while (added) {
					added = false;
					Set<String> S = new HashSet<String>(scores.getCounter(A).keySet());
					for (String key : scores.getCounter(A).keySet()) {
						for (UnaryRule r : grammar.getUnaryRulesByChild(key)) {
							// double prob = score.get(key) * r.getScore();
							double prob = scores.getCount(A, r.getParent()) * r.getScore();
							// if (!score.containsKey(r.getParent()) ||
							// score.get(r.getParent()) < prob) {
							if (!scores.getCounter(A).containsKey(r.getParent())
									|| scores.getCount(A, r.getParent()) < prob) {

								// Filled the CKY table and track the split
								// information
								scores.setCount(A, r.getParent(), prob);

								// score.put(r.getParent(), prob);
								back.put(r.getParent(), new Triplet<Integer, String, String>(-1, r.getChild(), ""));
								backs.put(new Triplet<Integer, Integer, String>(begin, end, r.getParent()),
										new Triplet<Integer, String, String>(-1, "", key));

								added = true;
							}
						}
					}
				}

				// Add score into table, scoreIdx will store the index of
				// score and back locate in their list for given begin/end
				// We use it for backtracking.
				scoreIdx[begin][end] = scoreTable.size();
				// scoreTable.add(score);
				backTable.add(back);
			}
		}

		// Backtrack to build tree
		Tree<String> bestTree = buildTree(0, sentence.size(), "ROOT");
		return TreeAnnotations.unAnnotateTree(bestTree);
	}

	private Tree<String> buildTree(int begin, int end, String tag) {
		Triplet<Integer, String, String> tmp = backs.get(new Triplet<Integer, Integer, String>(begin, end, tag));
		Tree<String> result;
		if (tmp == null) {
			return new Tree<String>(tag);
		}

		if (tmp.getFirst() == -1) {
			Tree<String> subTree = null;
			if (tmp.getThird().equals(tag))
				subTree = new Tree<String>(tmp.getThird());

			else
				subTree = buildTree(begin, end, tmp.getThird());
			result = new Tree<String>(tag, Collections.singletonList(subTree));
			return result;
		} else {
			Tree<String> leftTree = buildTree(begin, tmp.getFirst(), tmp.getSecond());
			Tree<String> rightTree = buildTree(tmp.getFirst(), end, tmp.getThird());
			List<Tree<String>> child = new ArrayList<Tree<String>>();
			child.add(leftTree);
			child.add(rightTree);
			result = new Tree<String>(tag, child);
			return result;

		}
	}
}
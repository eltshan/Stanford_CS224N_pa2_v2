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

	// Training
	public void train(List<Tree<String>> trainTrees) {
		List<Tree<String>> bt = new ArrayList<Tree<String>>();
		for (Tree<String> t : trainTrees) {
			bt.add(TreeAnnotations.annotateTree(t));
		}
		lexicon = new Lexicon(bt);
		grammar = new Grammar(bt);
	}

	public Tree<String> getBestParse(List<String> sentence) {
		CKY(sentence);
		Tree<String> resultTree = buildTree(0, sentence.size(), "ROOT");
		return TreeAnnotations.unAnnotateTree(resultTree);
	}

	private void CKY(List<String> sentence) {
		int N = sentence.size() + 1;

		for (int i = 0; i < N - 1; ++i) {
			String tmpPair = i + "+" + (i + 1);

			for (String nonterminal : lexicon.getAllTags()) {
				scores.setCount(tmpPair, nonterminal, lexicon.scoreTagging(sentence.get(i), nonterminal));
				backs.put(new Triplet<Integer, Integer, String>(i, i + 1, nonterminal),
						new Triplet<Integer, String, String>(-1, "", sentence.get(i)));
			}

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
							backs.put(new Triplet<Integer, Integer, String>(i, i + 1, r.getParent()),
									new Triplet<Integer, String, String>(-1, "", key));
							added = true;
						}
					}
				}
			}
		}

		for (int span = 2; span <= sentence.size(); ++span) {
			for (int begin = 0; begin <= sentence.size() - span; ++begin) {

				int end = begin + span;
				String A = begin + "+" + end;

				for (int split = begin + 1; split <= end - 1; ++split) {
					String B = begin + "+" + split;
					String C = split + "+" + end;

					for (String leftKey : scores.getCounter(B).keySet()) {
						for (BinaryRule br : grammar.getBinaryRulesByLeftChild(leftKey)) {
							if (!scores.getCounter(C).containsKey(br.getRightChild()))
								continue;
							double prob = scores.getCount(B, br.getLeftChild()) * scores.getCount(C, br.getRightChild())
									* br.getScore();
							if (!scores.getCounter(A).containsKey(br.getParent())
									|| scores.getCount(A, br.getParent()) < prob) {

								scores.setCount(A, br.getParent(), prob);
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
					Set<String> tmpSet = new HashSet<String>(scores.getCounter(A).keySet());
					for (String key : tmpSet) {
						for (UnaryRule r : grammar.getUnaryRulesByChild(key)) {
							double prob = scores.getCount(A, r.getParent()) * r.getScore();

							if (!scores.getCounter(A).containsKey(r.getParent())
									|| scores.getCount(A, r.getParent()) < prob) {
								scores.setCount(A, r.getParent(), prob);

								backs.put(new Triplet<Integer, Integer, String>(begin, end, r.getParent()),
										new Triplet<Integer, String, String>(-1, "", key));

								added = true;
							}
						}
					}
				}

			}
		}

	}

	private Tree<String> buildTree(int begin, int end, String nonterminal) {
		Triplet<Integer, String, String> tmp = backs
				.get(new Triplet<Integer, Integer, String>(begin, end, nonterminal));
		if (tmp == null) {
			return new Tree<String>(nonterminal);
		}

		if (tmp.getFirst() != -1) {
			List<Tree<String>> child = new ArrayList<Tree<String>>();

			Tree<String> left = buildTree(begin, tmp.getFirst(), tmp.getSecond());
			Tree<String> right = buildTree(tmp.getFirst(), end, tmp.getThird());
			child.add(left);
			child.add(right);
			return new Tree<String>(nonterminal, child);

		} else {
			Tree<String> tmpTree = null;
			if (tmp.getThird().equals(nonterminal))
				tmpTree = new Tree<String>(tmp.getThird());
			else
				tmpTree = buildTree(begin, end, tmp.getThird());
			return new Tree<String>(nonterminal, Collections.singletonList(tmpTree));

		}
	}
}
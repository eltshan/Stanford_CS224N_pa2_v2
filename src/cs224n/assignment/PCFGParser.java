package cs224n.assignment;

import cs224n.assignment.Grammar.BinaryRule;
import cs224n.assignment.Grammar.UnaryRule;
import cs224n.ling.Tree;
import cs224n.util.CounterMap;
import cs224n.util.Pair;
import cs224n.util.Triplet;

import java.util.*;

/**
 * The CKY PCFG Parser you will implement.
 */
public class PCFGParser implements Parser {
	private Grammar grammar;
	private Lexicon lexicon;

	CounterMap<String, String> score = new CounterMap<String, String>();
	HashMap<Triplet<Integer, Integer, String>, Triplet<Integer, String, String>> back = new HashMap<Triplet<Integer, Integer, String>, Triplet<Integer, String, String>>();

	public void train(List<Tree<String>> trainTrees) {
		// TODO: before you generate your grammar, the training trees
		// need to be binarized so that rules are at most binary
		List<Tree<String>> bTrees = new ArrayList<Tree<String>>();
		for (Tree<String> tree : trainTrees) {
			bTrees.add(TreeAnnotations.annotateTree(tree));
		}
		lexicon = new Lexicon(bTrees);
		grammar = new Grammar(bTrees);
	}

	public Tree<String> getBestParse(List<String> sentence) {
		// TODO: implement this method
		Tree<String> bestTree = buildTree(0, sentence.size(), "ROOT");
		return TreeAnnotations.unAnnotateTree(bestTree);
	}

	public void CKY(List<String> sentence) {
		int N = sentence.size() + 1;
		String tmpPair = null;
		for (int i = 0; i < N - 1; ++i) {
			tmpPair = i + "+" + (i + 1);
			for (String tag : lexicon.getAllTags()) {
				if (lexicon.scoreTagging(sentence.get(i), tag) > 0) {
					score.setCount(tmpPair, tag, lexicon.scoreTagging(sentence.get(i), tag));
					back.put(new Triplet<Integer, Integer, String>(i, i + 1, tag),
							new Triplet<Integer, String, String>(-1, "", sentence.get(i)));
				}

			}

			// handling unary rule
			boolean added = true;
			while (added) {
				added = false;
				for (String B : score.getCounter(tmpPair).keySet()) {
					for (UnaryRule r : grammar.getUnaryRulesByChild(B)) {
						double prob = score.getCounter(tmpPair).getCount(B) * r.score;
						if (!score.getCounter(tmpPair).containsKey(r.getParent())
								|| score.getCount(tmpPair, r.getParent()) < prob) {
							score.setCount(tmpPair, r.getParent(), prob);
							back.put(new Triplet<Integer, Integer, String>(i, i + 1, r.getParent()),
									new Triplet<Integer, String, String>(-1, "", B));
							added = true;

						}
					}
				}
			}
		}

		for (int span = 2; span < N; ++span) {
			for (int begin = 0; begin < N - span; ++begin) {
				int end = begin + span;
				String A = begin + "+" + end;

				for (int split = begin + 1; split < end; ++split) {
					String B = begin + "+" + split;
					String C = split + "+" + end;
					for (String B_key : score.getCounter(B).keySet()) {

						for (BinaryRule br : grammar.getBinaryRulesByLeftChild(B_key)) {
							if (!score.getCounter(C).containsKey(br.getRightChild()))
								continue;
							double prob = score.getCount(B, br.getLeftChild()) * score.getCount(C, br.getRightChild())
									* br.getScore();
							if (score.getCount(A, br.getParent()) < prob) {
								score.setCount(A, br.getParent(), prob);
								back.put(new Triplet<Integer, Integer, String>(begin, end, br.getParent()),
										new Triplet<Integer, String, String>(split, br.getLeftChild(),
												br.getRightChild()));
							}

						}

					}

				}
				boolean added = true;
				while (added) {
					added = false;
					for (String key : score.getCounter(A).keySet()) {
						for (UnaryRule r : grammar.getUnaryRulesByChild(key)) {
							double prob = score.getCount(A, key) * r.getScore();
							if (score.getCount(A, r.getParent()) > 0 && score.getCount(A, r.getParent()) < prob) {
								// Filled the CKY table and track the split
								// information
								score.setCount(A, r.getParent(), prob);
								back.put(new Triplet<Integer, Integer, String>(begin, end, r.getParent()),
										new Triplet<Integer, String, String>(-1, "", key));

								added = true;
							}
						}
					}
				}
			}

		}

	}

	private Tree<String> buildTree(int begin, int end, String tag) {
		Triplet<Integer, String, String> tmp = back.get(new Triplet<Integer, Integer, String>(begin, end, tag));
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

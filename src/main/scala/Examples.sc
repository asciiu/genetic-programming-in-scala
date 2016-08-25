import scala.collection.immutable._
import model._
import GP._

val count = 1000
val maxDepth = 5
val terminalSet = IndexedSeq(Var('x)) ++ 1f.to(5f, 1f).map(Con)
val functionSet = IndexedSeq(Add, Sub, Div, Mul)
val initial = rampHalfHalf(count, maxDepth, functionSet, terminalSet).toVector
def pow(a: Float, b: Float): Float = Math.pow(a, b).toFloat
val cases = (-1f).to(1f, 0.05f).map(x => (Map('x -> x), pow(x, 2) - x - 2)).toMap

val sortedTrees = initial.map { tree =>
  tree -> fitness(cases)(tree)
} sortBy { case (_, fitness) =>
  fitness
} map { case (tree, _) =>
  tree
}

val replicas = sortedTrees.take(initial.length / 100 * 19)

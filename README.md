## Genetic programming in scala

### Introduction

I hope that one day software engineers will obsolete ourselves. That is, we will write an application capable of writing all other applications for us. Genetic programming (GP) is about just that: creating programs that solve problems based on expected outputs for given inputs. As a colleague of mine put it, you write the unit tests and the computer does the rest.

Most, if not all, of the algorithms implemented here are based on the excellent descriptions in _A Field Guide to Genetic Programming_. I've concentrated on readability and correctness as opposed to speed of execution or even reliability, so please excuse the lacklustre performance and the fact that tail recursion is not used in places. Also note that the algorithms implemented here are, to quote the authors, "the simplest thing that could possibly work".

### Representing programs

There are three main steps in GP (2 and 3 are repeated):

1. Generating a population of programs;
2. Evaluating the fitness of those programs; and
3. Evolving a new, hopefully fitter, population of programs.

In order to do any of those things, however, we need to be able to represent programs in some way. This is normally done by means of an abstract syntax tree (AST). The AST considered here is handcrafted and only capable of representing simple polynomial expressions. There are, however, many tools capable of automatically generating ASTs from grammar files (my personal favourite being ANTLR). Here is the handcrafted AST:

###### model/Exp.scala

```scala
package model

sealed trait Exp

case class Con(value: Float) extends Exp

case class Var(name: Symbol) extends Exp

sealed trait BinOp extends Exp {
  def lhs: Exp
  def rhs: Exp
}

case class Add(lhs: Exp, rhs: Exp) extends BinOp

case class Sub(lhs: Exp, rhs: Exp) extends BinOp

case class Mul(lhs: Exp, rhs: Exp) extends BinOp

case class Div(lhs: Exp, rhs: Exp) extends BinOp

object Exp {
  import scala.util.Try
  def eval(exp: Exp, st: ST): Float = exp match {
    case Con(value) => value
    case Var(name) => st(name)
    case Add(lhs, rhs) => eval(lhs, st) + eval(rhs, st)
    case Sub(lhs, rhs) => eval(lhs, st) - eval(rhs, st)
    case Mul(lhs, rhs) => eval(lhs, st) * eval(rhs, st)
    case Div(lhs, rhs) => Try(eval(lhs, st) / eval(rhs, st)).getOrElse(1f)
  }
}
```

And a type alias from ST to Map[Symbol, Float] for the symbol table:

###### model/package.scala

```scala
import scala.collection.immutable._

package object model {
  type ST = Map[Symbol, Float]
}
```

There are a three design decisions in the above code worth discussing.

First, constants and variables are always `Float`. There are two main reasons for this: type safety and memory consumption. Creating a generic AST and accompanying symbol table(s) would be reasonably straight forward, but writing GP algorithms that operate on the AST in a type safe fashion would not be so simple.

Second, the `eval` method never throws `java.lang.ArithmeticException: / by zero` when performing division returning `1` instead. This ensures both type and evaluation safety and, again, makes it easier to write GP algorithms that operate on ASTs without encountering runtime exceptions.

Third, and finally, both `Exp` and `BinOp` are sealed traits. This makes pattern matching easier because the compiler will warn us about incomplete matches instead of leaving us at the mercy of runtime exceptions.

#### Example ASTs

In order to better understand what we are trying to achieve automatically it is useful to manually create some ASTs and plot the results of evaluating them for various values of `x`.

##### Second degree polynomial

Here is an AST for the second degree polynomial `f(x) = x^2 - x - 2`:

```scala
import model._
Sub(Sub(Mul(Var('x), Var('x)), Var('x)), Con(2))
```

It can be evaluated for various values of `x` like so:

```scala
import model._
val exp = Sub(Sub(Mul(Var('x), Var('x)), Var('x)), Con(2))
Exp.eval(exp, Map('x -> -3)) // res0: Float = 10
Exp.eval(exp, Map('x -> 0)) // res0: Float = -2
Exp.eval(exp, Map('x -> 3)) // res0: Float = 4
```

This plots shows the result of the expression for values of `x` between `-3` and `3`:

![Second degree polynomial](SecondDegreePolynomial.png "Second degree polynomial")

##### Third degree polynomial

Here is another, slightly more complex, AST for the third degree polynomial `f(x) = x^3 / 4 + 3x^2 / 4 - 3x / 2 - 2`:

```scala
import model._
Sub(Sub(Add(Div(Mul(Var('x),Mul(Var('x),Var('x))),Con(4)),Div(Mul(Con(3),Mul(Var('x),Var('x))),Con(4))),Div(Mul(Con(3),Var('x)),Con(2))),Con(2))
```

As before, the result for various values of `x` can be determined like so:

```scala
import model._
val exp = Sub(Sub(Add(Div(Mul(Var('x),Mul(Var('x),Var('x))),Con(4)),Div(Mul(Con(3),Mul(Var('x),Var('x))),Con(4))),Div(Mul(Con(3),Var('x)),Con(2))),Con(2))
Exp.eval(exp, Map('x -> -2)) // res1: Float = 2.0
Exp.eval(exp, Map('x -> 0)) // res2: Float = -2.0
Exp.eval(exp, Map('x -> 2)) // res3: Float = 0.0
```

And here is the plot:

![Third degree polynomial](ThirdDegreePolynomial.png "Third degree polynomial")

From looking at both the AST and the chart you can hopefully see that the third degree polynomial will be more difficult to generate automatically than the second degree polynomial. These two expressions, one simpler and one more complex, will make good test cases for our GP solution.

### Generating a population of programs

In GP, the leafs of an AST, whether they be constants or variables, are referred to as the terminal set. The branches whether they be unary, binary or some other arity function are referred to as the function set. Using the AST defined above the following terminal and functional sets can be created:

```scala
val terminalSet = IndexedSeq(Var('x)) ++ 1f.to(5f, 1f).map(Con)
val functionSet = IndexedSeq(Add, Sub, Div, Mul)
```

With these sets it's possible to generate an AST of some arbitrary depth `depth`. The simplest way to do this is to create a `full` tree. The algorithm is reasonably simple. If the depth has been reached then return a random terminal from the terminal set. Otherwise return a random function passing the result of the next recursion as it's argument(s). Here is a method that does that:

```scala
def full(
    depth: Int,
    functions: IndexedSeq[(Exp, Exp) => Exp],
    terminals: IndexedSeq[Exp]): Exp = {
  def loop(i: Int): Exp = {
    if (i == depth) {
      random(terminals)
    } else {
      random(functions)(loop(i + 1), loop(i + 1))
    }
  }
  loop(0)
}

def random[T](elements: IndexedSeq[T]): T = {
  elements(Random.nextInt(elements.length))
}
```

Here are some example ASTs with depths `1`, `2` and `3` generated with the `full` method:

![Full trees](FullTrees.png "Full trees")

The `full` method is simple and definitely works, but all the trees for a given depth have the exactly same number of leafs. This leads to a surprisingly uniform population. The `grow` method resolves this issue by randomly stopping the recursion:

```scala
def grow(
    depth: Int,
    functions: IndexedSeq[(Exp, Exp) => Exp],
    terminals: IndexedSeq[Exp]): Exp = {
  def randomStop: Boolean = {
    val tl = terminals.length.toFloat
    val fl = functions.length.toFloat
    random() < tl / (tl + fl)
  }
  def loop(i: Int): Exp = {
    if (i == depth || randomStop) {
      random(terminals)
    } else {
      random(functions)(loop(i + 1), loop(i + 1))
    }
  }
  loop(0)
}

def random(): Float = Random.nextFloat()
```

Here are three example ASTs all of depth `3` generated with `grow` method:

![Grow trees](GrowTrees.png "Grow trees")

These two methods can be composed together to create the method `rampHalfHalf`. The idea behind this is to use `grow` for one half of the population and `full` for the other. Further, the method starts at `depth` 1 and ramps up to some `maxDepth` (as opposed to all trees being the same `depth`):

```scala
def rampHalfHalf(
      count: Int,
      maxDepth: Int,
      functions: IndexedSeq[(Exp, Exp) => Exp],
      terminals: IndexedSeq[Exp]): Set[Exp] = {
    @tailrec
    def loop(acc: Set[Exp], i: Int, depth: Int): Set[Exp] = {
      if(i == count) {
        acc
      } else {
        val tree = if (i % 2 == 0) {
          full(depth, functions, terminals)
        } else {
          grow(depth, functions, terminals)
        }
        val nextDepth = if (depth == maxDepth) 1 else depth + 1
        if (acc.contains(tree)) {
          loop(acc, i, nextDepth)
        } else {
          loop(acc + tree, i + 1, nextDepth)
        }
      }
    }
    loop(Set.empty, 0, 1)
  }
```

The method above is a little finicky for several reasons. First, instead of comparing `acc.size` to `count` the value `i` is used. This is because the `size` method on set is inefficient. Second, if the `acc` set already contains a tree then `i` is not incremented. Third, and contrary to `i`, `depth` is always incremented unless the `maxDepth` has been reached and in this case it is set to `1`. This is because, especially for large values of `count`, it is possible that all permutations of trees at a certain `depth` have been generated. If `depth` were not incremented then the recursion could be infinite.

With the above, it is now possible to create an initial population:

```scala
val count = 10000
val maxDepth = 10
val terminalSet = IndexedSeq(Var('x)) ++ 1f.to(5f, 1f).map(Con)
val functionSet = IndexedSeq(Add, Sub, Div, Mul)
val initial = rampHalfHalf(count, maxDepth, functionSet, terminalSet).toVector
```

# Evaluating fitness

Evaluating fitness is easier than you might think. Given some input there is an expected output. The trick is to select the right inputs. Once that is done, however, evaluating fitness is just a matter of measuring the difference between the expected and actual outputs. For anyone who has done unit testing with multiple test cases this should feel familiar. The only part that might feel alien is measuring the difference and then reducing that measure into one figure for all test cases. For anyone with a machine learning background this will feel quite comfortable.
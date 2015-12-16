/*
Copyright 2015 ThoughtWorks, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.thoughtworks.each

import com.thoughtworks.each.core.MonadicTransformer
import com.thoughtworks.each.core.MonadicTransformer._

import scala.language.existentials
import scala.annotation.compileTimeOnly
import scala.language.experimental.macros
import scala.language.{higherKinds, implicitConversions}
import scalaz.effect.MonadCatchIO
import scalaz._
import scalaz.syntax.{FoldableOps, TraverseOps}

object Monadic {

  @inline
  implicit final class ToMonadicLoopOps[F[_], A](underlying: F[A]) {

    def monadicLoop = new MonadicLoop(underlying)

  }

  @inline
  implicit def getUnderlying[F[_], A](monadicLoop: MonadicLoop[F, A]) = monadicLoop.underlying

  object MonadicLoop {

    @inline
    implicit def toFoldableOps[F[_] : Foldable, A](monadicLoop: MonadicLoop[F, A]) = {
      scalaz.syntax.foldable.ToFoldableOps(monadicLoop.underlying)
    }

    @inline
    implicit def toTraverseOps[F[_] : Traverse, A](monadicLoop: MonadicLoop[F, A]) = {
      scalaz.syntax.traverse.ToTraverseOps(monadicLoop.underlying)
    }

    @inline
    implicit def toMonadPlusOps[F[_] : MonadPlus, A](monadicLoop: MonadicLoop[F, A]) = {
      scalaz.syntax.monadPlus.ToMonadPlusOps(monadicLoop.underlying)
    }

  }

  final class MonadicLoop[F0[_], A](val underlying: F0[A]) {

    type F[X] = F0[X]

    type Element = A

    @inline
    def toFoldableOps(implicit foldable: Foldable[F]) = scalaz.syntax.foldable.ToFoldableOps(underlying)

    @inline
    def toTraverseOps(implicit traverse: Traverse[F]) = scalaz.syntax.traverse.ToTraverseOps(underlying)

    @compileTimeOnly("`foreach` must be inside `monadic`, `throwableMonadic`, or `catchIoMonadic`.")
    def foreach[U](f: A => U)(implicit F: Foldable[F]): Unit = ???

    @compileTimeOnly("`map` must be inside `monadic`, `throwableMonadic`, or `catchIoMonadic`.")
    def map[B](f: A => B)(implicit traverse: Traverse[F]): MonadicLoop[F, B] = ???

    @compileTimeOnly("`flatMap` must be inside `monadic`, `throwableMonadic`, or `catchIoMonadic`.")
    def flatMap[B](f: A => F[B])(implicit traverse: Traverse[F], bind: Bind[F]): MonadicLoop[F, B] = ???

    @compileTimeOnly("`withFilter` must be inside `monadic`, `throwableMonadic`, or `catchIoMonadic`.")
    def withFilter(p: A => Boolean)(implicit traverse: Traverse[F], monadPlus: MonadPlus[F]): MonadicLoop[F, A] = ???

  }

  /**
    * An implicit view to enable `for` `yield` comprehension for a monadic value.
    *
    * @param v the monadic value.
    * @param F0 a helper to infer types.
    * @tparam FA type of the monadic value.
    * @return the temporary wrapper that contains the `each` method.
    */
  @inline
  implicit def toMonadicLoopOpsUnapply[FA](v: FA)(implicit F0: Unapply[Foldable, FA]) = {
    new ToMonadicLoopOps[F0.M, F0.A](F0(v))
  }

  /**
    * The temporary wrapper that contains the `each` method.
    *
    * @param underlying the underlying monadic value.
    * @tparam F the higher kinded type of the monadic value.
    * @tparam A the element type of of the monadic value.
    */
  final case class EachOps[F[_], A](private val underlying: F[A]) {

    /**
      * Semantically, returns the result in the monadic value.
      *
      * This macro must be inside a `monadic`
      * or a `catchIoMonadic`  block.
      *
      * This is not a real method, thus it will never actually execute.
      * Instead, the call to this method will be transformed to a monadic expression.
      * The actually result is passing as a parameter to some [[scalaz.Monad#bind]] and [[scalaz.Monad#point]] calls
      * instead of as a return value.
      *
      * @return the result in the monadic value.
      */
    @compileTimeOnly("`each` must be inside `monadic`, `throwableMonadic`, or `catchIoMonadic`.")
    def each: A = ???

  }

  /**
    * An implicit view to enable `.each` for a monadic value.
    *
    * @param v the monadic value.
    * @param F0 a helper to infer types.
    * @tparam FA type of the monadic value.
    * @return the temporary wrapper that contains the `each` method.
    */
  @inline
  implicit def toEachOpsUnapply[FA](v: FA)(implicit F0: Unapply[Bind, FA]) = new EachOps[F0.M, F0.A](F0(v))

  /**
    * An implicit view to enable `.each` for a monadic value.
    *
    * @param v the monadic value.
    * @return the temporary wrapper that contains the `each` method.
    */
  @inline
  implicit def toEachOps[F[_], A](v: F[A]) = new EachOps(v)


  /**
    * @usecase def monadic[F[_]](body: AnyRef)(implicit monad: Monad[F]): F[body.type] = ???
    *
    *          Captures all the result in the `body` and converts them into a `F`.
    *
    *          Note that `body` must not contain any `try` / `catch` / `throw` expressions.
    *
    * @tparam F the higher kinded type of the monadic expression.
    * @param body the imperative style expressions that will be transform to monadic style.
    * @param monad the monad that executes expressions in `body`.
    * @return
    */
  @inline
  def monadic[F[_]] = new PartialAppliedMonadic[Monad, F, UnsupportedExceptionHandlingMode.type]

  /**
    * @usecase def catchIoMonadic[F[_]](body: AnyRef)(implicit monad: MonadCatchIO[F]): F[body.type] = ???
    *
    *          Captures all the result in the `body` and converts them into a `F`.
    *
    *          Note that `body` may contain any `try` / `catch` / `throw` expressions.
    *
    * @tparam F the higher kinded type of the monadic expression.
    * @param body the imperative style expressions that will be transform to monadic style.
    * @param monad the monad that executes expressions in `body`.
    * @return
    */
  @inline
  def catchIoMonadic[F[_]] = new PartialAppliedMonadic[MonadCatchIO, F, MonadCatchIoMode.type]

  @inline
  implicit def eitherTMonadThrowable[F[_], G[_[_], _]](implicit F0: Monad[({type g[y] = G[F, y]})#g]): MonadThrowable[
    ({type f[x] = EitherT[({type g[y] = G[F, y]})#g, Throwable, x]})#f
    ] = {
    EitherT.eitherTMonadError[({type g[y] = G[F, y]})#g, Throwable]
  }

  @inline
  implicit def lazyEitherTMonadThrowable[F[_], G[_[_], _]](implicit F0: Monad[({type g[y] = G[F, y]})#g]): MonadThrowable[
    ({type f[x] = LazyEitherT[({type g[y] = G[F, y]})#g, Throwable, x]})#f
    ] = {
    LazyEitherT.lazyEitherTMonadError[({type g[y] = G[F, y]})#g, Throwable]
  }


  /**
    * A [[scalaz.Monad]] that supports exception handling.
    *
    * Note this is a simplified version of [[scalaz.MonadError]].
    *
    * @tparam F the higher kinded type of the monad.
    */
  type MonadThrowable[F[_]] = MonadError[F, Throwable]

  /**
    * @usecase def throwableMonadic[F[_]](body: AnyRef)(implicit monad: MonadThrowable[F]): F[body.type] = ???
    *
    *          Captures all the result in the `body` and converts them into a `F`.
    *
    *          Note that `body` may contain any `try` / `catch` / `throw` expressions.
    *
    * @tparam F the higher kinded type of the monadic expression.
    * @param body the imperative style expressions that will be transform to monadic style.
    * @param monad the monad that executes expressions in `body`.
    * @return
    */
  @inline
  def throwableMonadic[F[_]] = new PartialAppliedMonadic[MonadThrowable, F, MonadThrowableMode.type]

  /**
    * Partial applied function instance to convert a monadic expression.
    *
    * For type inferring only.
    *
    * @tparam M
    * @tparam F
    */
  final class PartialAppliedMonadic[M[_[_]], F0[_], Mode <: ExceptionHandlingMode] private[Monadic]() {

    type F[A] = F0[A]

    def apply[X](body: X)(implicit monad: M[F]): F[X] = macro PartialAppliedMonadic.MacroImplementation.apply

  }

  private object PartialAppliedMonadic {

    private[PartialAppliedMonadic] object MacroImplementation {

      def apply(c: scala.reflect.macros.whitebox.Context)(body: c.Tree)(monad: c.Tree): c.Tree = {
        import c.universe._
        //        c.info(c.enclosingPosition, showRaw(c.macroApplication), true)
        val Apply(Apply(TypeApply(Select(partialAppliedMonadicTree, _), List(asyncValueTypeTree)), _), _) = c.macroApplication


        val modeType: Type = partialAppliedMonadicTree.tpe.widen.typeArgs(2)

        val mode: ExceptionHandlingMode = if (modeType =:= typeOf[MonadCatchIoMode.type]) {
          MonadCatchIoMode
        } else if (modeType =:= typeOf[MonadThrowableMode.type]) {
          MonadThrowableMode
        } else if (modeType =:= typeOf[UnsupportedExceptionHandlingMode.type]) {
          UnsupportedExceptionHandlingMode
        } else {
          throw new IllegalStateException("Unsupported ExceptionHandlingMode")
        }
        val partialAppliedMonadicType = partialAppliedMonadicTree.tpe.widen
        val fType = partialAppliedMonadicType.typeArgs(1)

        val transformer = new MonadicTransformer[c.universe.type](c.universe, mode) {

          override def freshName(name: String) = c.freshName(name)

          override def fTree = SelectFromTypeTree(TypeTree(partialAppliedMonadicType), TypeName("F"))

          private val eachMethodSymbol = {
            val eachOpsType = typeOf[_root_.com.thoughtworks.each.Monadic.EachOps[({type T[F[_]] = {}})#T, _]]
            eachOpsType.member(TermName("each"))
          }

          private val monadciType = typeOf[_root_.com.thoughtworks.each.Monadic.MonadicLoop[({type T[F[_]] = {}})#T, _]]

          private val foreachMethodSymbol = monadciType.member(TermName("foreach"))

          private val mapMethodSymbol = monadciType.member(TermName("map"))

          private val flatMapMethodSymbol = monadciType.member(TermName("flatMap"))

          private val filterMethodSymbol = monadciType.member(TermName("withFilter"))

          private def hook(expectedType: Type, resultTree: Tree): Tree = {
            Apply(
              Select(
                New(TypeTree(expectedType)),
                termNames.CONSTRUCTOR
              ),
              List(resultTree)
            )
          }

          override val instructionExtractor: PartialFunction[Tree, Instruction] = {
            case origin@Apply(Apply(methodTree@Select(opsTree, _), List(bodyFunctionTree: Function)), List(traverseTree, monadPlusTree)) if methodTree.symbol == filterMethodSymbol => {
              Filter(
                Apply(Select(opsTree, TermName("toTraverseOps")), List(traverseTree)),
                bodyFunctionTree,
                monadPlusTree,
                origin.tpe.member(TermName("underlying")).typeSignatureIn(origin.tpe),
                hook(origin.tpe, _))
            }
            case origin@Apply(Apply(TypeApply(methodTree@Select(opsTree, _), List(resultTypeTree)), List(bodyFunctionTree: Function)), List(traverseTree, bindTree)) if methodTree.symbol == flatMapMethodSymbol => {
              FlatMap(
                Apply(Select(opsTree, TermName("toTraverseOps")), List(traverseTree)),
                resultTypeTree,
                bodyFunctionTree,
                bindTree,
                origin.tpe.member(TermName("underlying")).typeSignatureIn(origin.tpe),
                hook(origin.tpe, _))
            }
            case origin@Apply(Apply(TypeApply(methodTree@Select(opsTree, _), _), List(bodyFunctionTree: Function)), List(traverseTree)) if methodTree.symbol == mapMethodSymbol => {
              Map(
                Apply(Select(opsTree, TermName("toTraverseOps")), List(traverseTree)),
                bodyFunctionTree,
                origin.tpe.member(TermName("underlying")).typeSignatureIn(origin.tpe),
                hook(origin.tpe, _))
            }
            case Apply(Apply(TypeApply(methodTree@Select(opsTree, _), _), List(bodyFunctionTree: Function)), List(foldableTree)) if methodTree.symbol == foreachMethodSymbol => {
              Foreach(
                Apply(Select(opsTree, TermName("toFoldableOps")), List(foldableTree)),
                bodyFunctionTree)
            }
            case eachMethodTree@Select(eachOpsTree, _) if eachMethodTree.symbol == eachMethodSymbol => {
              val actualFType = eachOpsTree.tpe.typeArgs(0)
              val resultType = eachMethodTree.tpe
              val expectedType = appliedType(fType, List(resultType))
              val actualType = appliedType(actualFType, List(resultType))
              if (!(actualType <:< expectedType)) {
                c.error(
                  eachOpsTree.pos,
                  raw"""type mismatch;
 found   : ${show(actualType)}
 required: ${show(expectedType)}""")
              }
              Each(Select(Apply(Select(reify(_root_.com.thoughtworks.each.Monadic.EachOps).tree, TermName("unapply")), List(eachOpsTree)), TermName("get")))
            }
          }

        }
        val result = transformer.transform(body, monad)
        //        c.info(c.enclosingPosition, show(result), true)
        c.untypecheck(result)
      }
    }

  }

}
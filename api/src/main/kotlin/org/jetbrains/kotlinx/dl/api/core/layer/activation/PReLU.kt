/*
 * Copyright 2020 JetBrains s.r.o. and Kotlin Deep Learning project contributors. All Rights Reserved.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.kotlinx.dl.api.core.layer.activation

import org.jetbrains.kotlinx.dl.api.core.KGraph
import org.jetbrains.kotlinx.dl.api.core.initializer.Initializer
import org.jetbrains.kotlinx.dl.api.core.initializer.Zeros
import org.jetbrains.kotlinx.dl.api.core.regularizer.Regularizer
import org.jetbrains.kotlinx.dl.api.core.shape.numElements
import org.jetbrains.kotlinx.dl.api.core.shape.toLongArray
import org.jetbrains.kotlinx.dl.api.core.util.getDType
import org.tensorflow.Operand
import org.tensorflow.Shape
import org.tensorflow.op.Ops
import org.tensorflow.op.core.Variable

/**
 * Parametric Rectified Linear Unit.
 *
 * It follows:
 * ```
 * f(x) = alpha * x     if x < 0
 * f(x) = x             if x >= 0
 * ```
 * where `alpha` is a learnable weight and has the same shape as `x` (i.e. input).
 *
 * @property [alphaInitializer] Initializer instance for the weights.
 * @property [alphaRegularizer] Regularizer instance for the weights.
 * @property [sharedAxes] The axes along which to share learnable parameters.
 */
public class PReLU(
    public val alphaInitializer: Initializer = Zeros(),
    public val alphaRegularizer: Regularizer? = null,
    public val sharedAxes: IntArray? = null,
    name: String = ""
) : AbstractActivationLayer(name) {
    /**
     * TODO: support for constraint (alphaConstraint) should be added
     */
    private lateinit var alphaShape: Shape
    private lateinit var alpha: Variable<Float>
    private val alphaVariableName = if (name.isNotEmpty()) name + "_" + "alpha" else "alpha"

    override var weights: Map<String, Array<*>>
        get() = extractWeights(listOf(alphaVariableName))
        set(value) = assignWeights(value)
    override val paramCount: Int
        get() = alphaShape.numElements().toInt()

    init {
        isTrainable = true
    }

    override fun build(tf: Ops, kGraph: KGraph, inputShape: Shape) {
        val alphaShapeArray = inputShape.toLongArray().drop(1).toLongArray()
        if (sharedAxes != null) {
            for (axis in sharedAxes) {
                alphaShapeArray[axis - 1] = 1
            }
        }
        alphaShape = Shape.make(alphaShapeArray[0], *alphaShapeArray.drop(1).toLongArray())

        fanIn = inputShape.size(inputShape.numDimensions() - 1).toInt()
        fanOut = fanIn

        alpha = tf.withName(alphaVariableName).variable(alphaShape, getDType())
        alpha = addWeight(tf, kGraph, alphaVariableName, alpha, alphaInitializer, alphaRegularizer)
    }

    override fun forward(tf: Ops, input: Operand<Float>): Operand<Float> {
        // It's equivalent to: `-alpha * relu(-x) + relu(x)`
        val positive = tf.nn.relu(input)
        val negative = tf.math.mul(tf.math.neg(alpha), tf.nn.relu(tf.math.neg(input)))
        return tf.math.add(positive, negative)
    }

    override fun toString(): String =
        "PReLU(alphaInitializer=$alphaInitializer, alphaRegularizer=$alphaRegularizer, sharedAxes=$sharedAxes)"
}

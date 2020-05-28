package io.kotless.parser.utils.psi.visitor

import io.kotless.parser.utils.psi.getTargets
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import java.util.*
import kotlin.reflect.KClass

abstract class KtReferenceFollowingVisitor(
    private val binding: BindingContext,
    protected val references: Stack<KtElement> = Stack(),
    protected val targets: Stack<KtElement> = Stack()
) : KtDefaultVisitor() {
    protected open fun shouldFollowReference(reference: KtElement, target: KtElement) = true

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        val targets = expression.getTargets(binding)
        for (target in targets) {
            if (target in this.targets || !shouldFollowReference(expression, target)) continue

            this.references.push(expression)
            this.targets.push(target)
            target.accept(this)
            this.targets.pop()
            this.references.pop()
        }

        super.visitReferenceExpression(expression)
    }

    fun <T : Any> PsiElement.parentsWithReferences(klass: KClass<T>, filter: (T) -> Boolean = { true }): Sequence<T> = sequence {
        val toIterate = listOf(this@parentsWithReferences) + this@KtReferenceFollowingVisitor.references
        for (reference in toIterate) {
            yieldAll(reference.parents.filterIsInstance(klass.java).filter { filter(it) })
        }
    }
}

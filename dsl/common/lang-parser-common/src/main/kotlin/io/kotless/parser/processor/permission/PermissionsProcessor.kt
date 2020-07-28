package io.kotless.parser.processor.permission

import io.kotless.AwsResource
import io.kotless.Permission
import io.kotless.PermissionLevel
import io.kotless.dsl.lang.DynamoDBTable
import io.kotless.dsl.lang.S3Bucket
import io.kotless.dsl.lang.SNSTopic
import io.kotless.dsl.lang.SSMParameters
import io.kotless.parser.utils.psi.annotation.getAnnotations
import io.kotless.parser.utils.psi.annotation.getArrayValue
import io.kotless.parser.utils.psi.annotation.getEnumValue
import io.kotless.parser.utils.psi.annotation.getValue
import io.kotless.parser.utils.psi.visitAnnotatedWithReferences
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext

object PermissionsProcessor {
    private val PERMISSION_ANNOTATIONS_CLASSES = listOf(S3Bucket::class, SSMParameters::class, DynamoDBTable::class, SNSTopic::class)

    fun process(func: KtExpression, context: BindingContext): Set<Permission> {
        val permissions = HashSet<Permission>()

        func.visitAnnotatedWithReferences(context, visitOnce = true) {
            permissions.addAll(processAnnotated(it, context))
        }

        return (permissions + Permission(AwsResource.CloudWatchLogs, PermissionLevel.ReadWrite, setOf("*"))).toSet()
    }

    private fun processAnnotated(expression: KtAnnotated, context: BindingContext): HashSet<Permission> {
        val permissions = HashSet<Permission>()

        PERMISSION_ANNOTATIONS_CLASSES.forEach { routeClass ->
            expression.getAnnotations(context, routeClass).forEach { annotation ->
                when (routeClass) {
                    S3Bucket::class -> {
                        val id = annotation.getValue(context, S3Bucket::bucket)!!
                        val level = annotation.getEnumValue(context, S3Bucket::level)!!
                        permissions.add(Permission(AwsResource.S3, level, setOf("$id/*")))
                    }
                    SSMParameters::class -> {
                        val id = annotation.getValue(context, SSMParameters::prefix)!!
                        val level = annotation.getEnumValue(context, SSMParameters::level)!!
                        permissions.add(Permission(AwsResource.SSM, level, setOf("parameter/$id*")))
                    }
                    DynamoDBTable::class -> {
                        val id = annotation.getValue(context, DynamoDBTable::table)!!
                        val level = annotation.getEnumValue(context, DynamoDBTable::level)!!
                        val indexes = annotation.getArrayValue(context, DynamoDBTable::indexes) ?: emptyArray()
                        val ids = setOf("table/$id") + indexes.map { "table/$id/index/$it" }

                        permissions.add(Permission(AwsResource.DynamoDB, level, ids))
                    }
                    SNSTopic::class -> {
                        val id = annotation.getValue(context, SNSTopic::topic)!!
                        val level = annotation.getEnumValue(context, SNSTopic::level)!!
                        permissions.add(Permission(AwsResource.SNS, level, setOf(id)))
                    }
                }
            }
        }

        return permissions
    }
}

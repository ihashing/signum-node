package brs.common

import brs.Blockchain
import brs.DependencyProvider
import brs.db.store.TradeStore
import brs.fluxcapacitor.FluxCapacitor
import brs.fluxcapacitor.FluxCapacitorImpl
import brs.fluxcapacitor.FluxEnable
import brs.fluxcapacitor.FluxValue
import brs.http.common.Parameters.DEADLINE_PARAMETER
import brs.http.common.Parameters.FEE_NQT_PARAMETER
import brs.http.common.Parameters.PUBLIC_KEY_PARAMETER
import brs.http.common.Parameters.SECRET_PHRASE_PARAMETER
import brs.props.Prop
import brs.props.PropertyService
import brs.props.PropertyServiceImpl
import brs.props.Props
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.assertTrue
import javax.servlet.http.HttpServletRequest
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.createType

object QuickMocker {
    fun dependencyProvider(vararg dependencies: Any): DependencyProvider {
        val classToDependency = dependencies
            .map { it::class.createType() to it }
            .toMap()
            .toMutableMap()
        assertTrue("Duplicate dependencies found (two or more dependencies of the same type were provided)", dependencies.size == classToDependency.size)

        val dp = DependencyProvider()
        dp::class.members.forEach { member ->
            if (member is KMutableProperty<*>) {
                member.setter.call(dp, classToDependency[member.returnType] ?: return@forEach)
                classToDependency.remove(member.returnType)
            }
        }
        assertTrue("Not all dependencies can go into dependency provider, these can't: ${classToDependency.keys}", classToDependency.isEmpty())
        return dp
    }

    fun defaultPropertyService() = mock<PropertyService> {
        onGeneric { get(any<Prop<Any>>()) } doAnswer {
            when (it.getArgument<Prop<Any>>(0).defaultValue) {
                is Boolean -> return@doAnswer false
                is Int -> return@doAnswer -1
                is String -> return@doAnswer ""
                is List<*> -> return@doAnswer emptyList<String>()
                else -> null
            }
        }
    }

    fun fluxCapacitorEnabledFunctionalities(vararg enabledToggles: FluxEnable): FluxCapacitor {
        val mockCapacitor = mock<FluxCapacitor> {
            on { it.getValue(any<FluxValue<Boolean>>()) } doReturn false
            on { it.getValue(any<FluxValue<Boolean>>(), any()) } doReturn false
        }
        for (ft in enabledToggles) {
            whenever(mockCapacitor.getValue(eq(ft))).doReturn(true)
            whenever(mockCapacitor.getValue(eq(ft), any())).doReturn(true)
        }
        return mockCapacitor
    }

    fun latestValueFluxCapacitor(): FluxCapacitor {
        val blockchain = mock<Blockchain>()
        val propertyService = mock<PropertyService>()
        whenever(blockchain.height).doReturn(Integer.MAX_VALUE)
        whenever(propertyService.get(eq(Props.DEV_TESTNET))).doReturn(false)
        return FluxCapacitorImpl(dependencyProvider(blockchain, propertyService))
    }

    fun httpServletRequest(vararg parameters: MockParam): HttpServletRequest {
        val mockedRequest = mock<HttpServletRequest>()

        for (mp in parameters) {
            whenever(mockedRequest.getParameter(mp.key)).doReturn(mp.value)
        }

        return mockedRequest
    }

    fun httpServletRequestDefaultKeys(vararg parameters: MockParam): HttpServletRequest {
        val paramsWithKeys = mutableListOf(listOf(MockParam(SECRET_PHRASE_PARAMETER, TestConstants.TEST_SECRET_PHRASE), MockParam(PUBLIC_KEY_PARAMETER, TestConstants.TEST_PUBLIC_KEY), MockParam(DEADLINE_PARAMETER, TestConstants.DEADLINE), MockParam(FEE_NQT_PARAMETER, TestConstants.FEE)))

        paramsWithKeys.addAll(listOf(parameters.toList()))

        return httpServletRequest(*paramsWithKeys.flatten().toTypedArray())
    }

    fun jsonObject(vararg parameters: JSONParam): JsonObject {
        val mockedRequest = JsonObject()

        for (mp in parameters) {
            mockedRequest.add(mp.key, mp.value)
        }

        return mockedRequest
    }

    class MockParam(val key: String, val value: String?) {

        constructor(key: String, value: Int?) : this(key, "" + value)

        constructor(key: String, value: Long?) : this(key, "" + value)

        constructor(key: String, value: Boolean?) : this(key, "" + value)
    }

    class JSONParam(val key: String, val value: JsonElement)
}

package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.DissociateAction
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.Key
import org.babyfish.jimmer.sql.KeyUniqueConstraint
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.OnDissociate
import org.babyfish.jimmer.sql.Serialized
import org.babyfish.jimmer.sql.Table
import tools.jackson.databind.JsonNode

/**
 * 插件持久化数据，同时承载插件配置值。业务身份是 `(plugin, key)`，[id] 仅为代理主键。
 *
 * [value] 与 [encryptedValue] 恰有一个非空，由 `ck_plugin_data_value` 保证；
 * 写入时必须同时给出两者（其一为 null），不能只写一列。
 */
@Entity
@KeyUniqueConstraint(noMoreUniqueConstraints = true)
@Table(name = "plugin_data")
interface PluginData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @Key
    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    val plugin: Plugin

    @IdView
    val pluginId: String

    @Key
    val key: String

    /** 明文值 */
    @Serialized
    val value: JsonNode?

    /** 密文值；加解密由 `PluginDataCipher` 负责，[key] 参与密钥派生 */
    val encryptedValue: ByteArray?
}

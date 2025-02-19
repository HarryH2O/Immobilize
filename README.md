### 🌟 **什么是Immobilize？**

**Immobilize** 是一款专为 Minecraft 服务器设计的轻量级插件，旨在通过灵活的“定身”功能帮助管理员高效控制玩家行为，或者朋友之间相互整蛊。无论是处理违规玩家、录制活动画面，还是临时限制特定操作，这款插件都能以极简指令和高度可定制性满足你的需求。



### ✨ **为什么选择 Immobilize？**

- **零学习成本**：指令逻辑直观，自动补全助力高效操作。

- **无侵入设计**：不修改玩家数据，解除定身后状态自动恢复。

- **开源免费**：代码透明，支持社区二次开发与定制。



---



### 🔧 **核心功能**

1. **全面行为封锁**

   - **禁止移动**：玩家无法移动、传送或跳跃

   - **交互限制**：禁用背包操作（打开/移动物品）、物品丢弃/拾取、攻击与交互

   - **命令封禁**：禁止使用聊天栏命令（仅允许op使用本插件指令）



2. **智能状态管理**

   - **定时解除**：通过 `-t:<秒>` 设置倒计时，或设为无限时长 `-t:-1`

   - **设置原因**：添加 `-r:<原因>` 记录操作理由，支持广播全服（`-r:原因.true`）

   - **防止偷袭**：定身期间玩家免疫伤害，无需担心损友偷袭！



3. **无缝权限集成**

   - **权限节点**：默认仅限 OP 使用，可自定义扩展。

   - **选择器支持**：兼容 `@a`、`@p` 等原版目标选择器，批量操作更便捷。



4. **高度可配置**

   - **多语言提示**：通过 `lang.yml` 自定义所有消息，支持颜色代码。

   - **支持热重载**：指令 `/ib admin reload` 即时生效，无需重启服务器。



---



### 🎮 **适用场景**

- **违规处理**：临时冻结疑似作弊玩家，配合调查。

- **活动管控**：在建筑比赛或 PvP 活动中限制玩家移动。

- **直播录制**：为视频创作者提供“静止镜头”功能。

- **新手引导**：强制玩家停留在教程区域。



---



### ⚙️ **兼容性与依赖**

- **需要的依赖**：ProtocolLib

- **版本支持**：基于Java8编写，理论支持Minecraft 1.16+

- **性能优化**：基于异步任务调度，0延迟卡顿

[测试服务器：spigot 1.20.1 ｜ 测试使用的ProtocolLib版本：5.3.0]



---



### 📥 **指令**

 

1. **作用**：

   /ib <true|false> [目标] [-t:<秒>] [-r:<原因>.<true|false>]

         true|false 开启/关闭定身模式

        [目标]省略则对自己生效

        -t:定身持续的时间，该后缀可省略可叠加，默认无限

        -r:该玩家被定身的原因，该后缀可省略可叠加，默认无

              true|false 是否将该玩家的被定身的消息广播

   /ib admin reload 重新加载配置文件



2.**权限节点**

    immobilize.command 主节点，默认仅允许4级op使用



---



立即下载，让玩家变得像冰块一样“冻”在原地！ 🚀


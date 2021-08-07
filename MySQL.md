[SQL查询执行流程](#select)

[SQL更新执行流程](#update)

[SQL删除执行流程](#delete)

[SQL排序执行流程](#sort)

[重建表](#rebuild)

[事务隔离](#transaction)

[索引](#index)

[锁](#lock)

[脏页](#dirty)

[数据库中对于数据行个数的统计](#count)

### <span id="select">SQL查询语句是如何执行的</span>

这样一条SQL的执行内部流程

```mysql
select * from T where ID=10;
```



MySQL的基本架构如下，大体分为Server层和引擎层

server层：连接器，分析器，优化器，执行器，查询缓存

引擎层：常见的引擎：MyISAM，InnoDB，Memory

<img src="/Users/didi/Documents/myBook/pic/MySQL基本架构.png" alt="MySQL基本架构" style="zoom:80%;" />



#### MySQL各个组件

> 连接器

> ​	负责跟客户端建立连接，获取权限、维持和管理连接
>
> ​	当连接客户端后，需要输入用户名和密码，以此获取你的权限，若管理员修改你的权限，也只是会在下次连接起生效
>
> ​	若连接后没有动作，连接会处于空闲状态，show processlist会发现，Command处于Sleep

<img src="/Users/didi/Documents/myBook/pic/连接状态.png" alt="连接状态" style="zoom:80%;" />

> ​	若连接后长时间没有动作，连接器会自动断开，再次请求会报断开连接的错误，需要重连，默认在8小时内没有动作则断开，
>
> ​	这个时间由wait_timeout控制
>
> ​	其中MySQL对于连接分为长连接以及短连接
>
> ​	长链接：指连接成功后，若客户端有持续连接，之后的请求均使用这个连接
>
> ​	短连接：指每次执行完很少几次查询就会断开连接，下次操作再重新建立连接
>
> ​	由于建立连接是个较复杂的操作，所以从尽量减少连接的角度出发，建议使用长连接
>
> ​	但是，使用大量长连接后会发现内存占用过大，甚至OOM（因为MySQL使用内存进行管理连接对象）
>
> ​		解决方案：
>
> ​			1.定时断开长连接（或者在判断内存占用过大时断开长连接），再操作再连接
>
> ​			2.MySQL 5.7后支持mysql_reset_connection操作，重新初始化连接，不需要进行重连那种复杂操作，将连接恢复成最初状态

​			

> 查询缓存
>
> ​	不建议使用MySQL的查询缓存，因为MySQL的缓存命中及其严格，稍有变化就需要更新缓存，而且查询时想命中缓存对SQL的眼球及其严格
>
> ​	且MySQL在8.0后删除查询缓存功能

> 分析器
>
> ​	首先会将SQL拆分成每个词，区分出每个词代表什么，比如说关键字，表，字段（即词法分析）
>
> ​	之后进行语法分析，判断是否满足MySQL语法，比如表是否存在等等

> 优化器
>
> ​	之后分析清楚那个表那个字段后，根据MySQL中建立的索引，优化器来选择使用哪些索引

```mysql
mysql> select * from t1 join t2 using(ID) where t1.c=10 and t2.d=20;
```

> ​	如，这个SQL，既可以选择t1索引后回表定位到t2的值，也可以选择t2索引回表定位到t1的值

> 执行器
>
> ​	通过优化器确认索引方案后，由执行器进行执行
>
> ​	执行后慢查询日志会看到rows_examined字段，代表这次SQL扫描的数据库行数

#### <span id="update"> SQL更新操作是如何执行的 </span>

更新操作在上面的查询操作的执行过程还涉及了redo log和bin log两个日志

#### redo log

<img src="/Users/didi/Documents/myBook/pic/redo log.png" alt="redo log" style="zoom:80%;" />

> ​		其中write pos为当前记录位置，check point为需要擦出的位置，其中两个标记之间的区域为可记录的区域
>
> ​		有了redo log的，InnoDB可保证MySQL异常重启，之前的提交记录不会丢失，这个能力成为crash-safe

#### bin log

> ​		redo log与bin log的区别
>
> ​			1.redo log是InnoDB独有，而bin log是server层，所有引擎均可使用
>
> ​			2.redo log是物理日志，而bin log是逻辑日志，前者记录了哪一数据页更新了哪一数据，后者记录的是该语句的原始逻辑
>
> ​			3.redo log是循环写，所写的内容不多，而bin log是追加写

#### 更新步骤（两阶段提交）

<img src="/Users/didi/Documents/myBook/pic/更新过程.png" alt="更新过程" style="zoom:50%;" />

> ​		两阶段提交：首先写redo log（处于prepare状态）--->写入bin log --->提交事务
>
> ​		为什么会有两阶段提交
>
> ​				首先若只记录一个日志
>
> ​						若只使用redo log：由于redo log是循环写，可恢复的内容有限
>
> ​						若只使用bin log：抛开一些历史原因不谈，假如进行了两次提交（第一次以提交，第二次未提及），
>
> ​						若出现异常重启，这两次的提交记录可能均丢失，但此时在bin log中对于第一次认为以提交，不会恢复
>
> ​				若使用两个记录，但不遵循两阶段提交，而是采用先写redo log后写bin log或者先写bin log后写redo log时，如在两个日志写之间异常重启，会出现两日志内容不一致（这个不一致在扩展备库时会出问题（因为从库是读bin log来写的））
>
>  		崩溃恢复策略：
>
> ​				若redo log出现完整commit，事务直接提交
>
> ​				若redo log只有prepare，根据bin log的状态（bin log对于commit有特殊的格式）
>
> ​							若bin log完整，则事务提交
>
> ​							若bin log不完整，则事务回滚

#### <span id="delete">SQL删除执行流程</span>

> ​		对于MySQL的删除问题：
>
> ​				在MySQL中删除一个数据，MySQL的做法是留住这个空间，若有另外的数据插入，且在这个数据在这个索引范围内的
>
> ​				，则复用这个空间；若删除的是一个数据页，，之后则会复用这个数据页
>
> ​		所以，在MySQL中删除数据时，会发现磁盘文件并未变化，所谓的删除操作仅仅是将这个数据标记为可复用。
>
> ​		正因为这个仅仅是标记为可复用，而不是进行空间的回收，所以对于被标记但并未复用的空间而言，就变成了“空洞”。、

> ​		解决：对表进行重建，以此来进行空间空洞的压缩

#### <span id="rebuild">重建表</span>

> ​		对于重建表来说，引入了一个临时文件temp_file（这个临时文件也是要占磁盘的）
>
> ​		MySQL 5.6之前，在重建表时进行数据的更改，是会丢失这些更改的数据，也就是说在重建表时是不能进行更新的，即不
>
> ​		是ONLINE的

<img src="/Users/didi/Documents/myBook/pic/5.6前重建表.png" alt="5.6前重建表" style="zoom:80%;" />

> ​		在MySQL 5.6后，可以在重建表的时候，进行更新操作，即是ONLINE的，他引入了一个row log 来记录在重建时的操作

<img src="/Users/didi/Documents/myBook/pic/5.6 后重建表.png" alt="5.6 后重建表" style="zoom:80%;" />

> ​		整理下来，对于MySQL的重建动作
>
> ​				1.重新统计索引信息：analyze table xxx
>
> ​				2.重建表：alter table xxx engine=innodb
>
> ​				3.前面两个效果的叠加：optimize table xxx

#### <span id="sort">SQL排序执行流程</span>

> ​		首先，MySQL提供了两种排序方式：全字段排序，rowId排序

##### 全字段排序

```mysql
select city,name,age from t where city='杭州' order by name limit 1000 ;
```

> ​		对于这样的一个查询来说
>
> ​		MySQL首先会线按照city索引将对应数据的主键查出，并回表查出完整数据
>
> ​		按照想要查出的字段，将对应字段放入sort_buffer（可设置sort_buffer_size的大小）
>
> ​		（若查出的数据大小小于sort_buffer_size，则会在sortBuffer内存中进行按照对应字段排序）
>
> ​		（若查出的数据大小大于sort_buffer_size，则会使用临时文件，且是多个临时文件，进行归并排序）

<img src="/Users/didi/Documents/myBook/pic/全字段排序.png" alt="全字段排序" style="zoom:50%;" />

##### rowId排序

> ​		若是需要查出的数据字段过多，导致单行数据过长，超过了MySQL的max_length_for_sort_data值时，进行rowId排序

```mysql
select city,name,age from t where city='杭州' order by name limit 1000 ;

SET max_length_for_sort_data=16;
```

> ​		这时的查询是这样的
>
> ​		根据city索引查出对应的主键值，回表查询对应主键的完整数据，
>
> ​		发现单行数据过长，则仅将需排序字段和主键字段放入sortBuffer中，
>
> ​		（若查出的数据大小小于sort_buffer_size，则会在sortBuffer内存中进行按照对应字段排序）
>
> ​		（若查出的数据大小大于sort_buffer_size，则会使用临时文件，且是多个临时文件，进行归并排序）
>
> ​		最后再回表查询对应主键的完整数据并返回

> ​		对于全字段排序和rowId排序来说，后者需要多次回表，所以并不优先选择

> ​		其实，对于排序问题，在数据库设计上可进行优化
>
> ​				1.将需要排序字段设计成自增，这样本身数据就是有序的
>
> ​				2.创建联合索引，比如上面的查询就可以创建city_name_age的联合索引，这样既是有序的，而且触发覆盖索引，不需要
>
> ​				回表

#### <span id="transaction">事务隔离</span>

> ​		数据库并发时的问题
>
> ​				1.脏读
>
> ​				2.不可重复读
>
> ​				3.幻读
>
> ​		对应出现了事务的隔离级别
>
> ​				1.读未提交
>
> ​				2.读已提交
>
> ​				3.可重复读（在InnoDB下，这个级别就可解决幻读问题）
>
> ​				4.序列化

<img src="/Users/didi/Documents/myBook/pic/事务隔离性.png" alt="事务隔离性" style="zoom:50%;" />

> ​		若是读未提交，事务A V1，V2，V3=2
>
> ​		若是读已提交，事务A V1=1，V2，V3=2
>
> ​		若是可重复读，事务A V1，V2=1，V3=2

##### MVCC（多版本并发控制）

> ​		视图：在MySQL中存在两个视图的概念
>
> ​				1.是view，是用于查询的虚拟表，查询方式和表一样，语法 create view ....
>
> ​				2.InnoDB在MVCC时实现的一致性视图，用于以及RR隔离级别的实现
>
> ​		查询（采取的快照读）
>
> ​				对于其他事务的视角：
>
> ​						1.对于未提交的，不可见
>
> ​						2.对于已提交的，若提交在视图创建之前，可见
>
> ​						2.对于已提交的，若提交在视图创建之后，不可见
>
> ​		更新
>
> ​				对于更新操作，应为先读后写，这个读为当前读，即取得最新的数据

<img src="/Users/didi/Documents/myBook/pic/MVCC查询.png" alt="MVCC查询" style="zoom:50%;" />

> ​		所以上图的结果（隔离级别为RR，k初始为1）
>
> ​				事务A：1，事务B：3，事务C：2

<img src="/Users/didi/Documents/myBook/pic/MVCC更新.png" alt="MVCC更新" style="zoom:50%;" />

> ​		对于当前读来说，更新需要拿到行锁，所以对于上图来说，事务B会被事务C阻塞，知道事务C提交后事务B才会执行

#### <span id="index">索引</span>

> ​		索引的数据模型
>
> ​				1.哈希表
>
> ​						优点：等值查询O(1)
>
> ​						问题：哈希表对于等值查询效率很高，但是对于范围查询做不到
>
> ​				2.有序数组
>
> ​						优点：等值查询以及范围查询均可以做到很高的效率
>
> ​						问题：在插入方面，增加/删除一个元素需要移动后面所有元素，效率低
>
> ​				3.二叉树
>
> ​						优点：查询，更新操作时间复杂度均O(logN)
>
> ​						问题：随着节点增加，树高高度越来越高，导致查询效率下降
>
> ​				4.N叉树（解决以上问题）

> ​		InnoDB使用的索引模型：B+树

```mysql
mysql> create table T(id int primary key, k int not null, name varchar(16),index (k))engine=InnoDB;
```

<img src="/Users/didi/Documents/myBook/pic/索引模型.png" alt="索引模型" style="zoom:50%;" />

> ​		此处会发现一个问题：ID索引以及k索引的节点内容不一样
>
> ​				涉及主键索引以及二级索引
>
> ​						二级索引节点储存主键索引
>
> ​						主键索引节点储存数据内容
>
> ​				当使用二级索引查询数据，首先是查到主键索引，再根据主键Id回表查到数据内容

> ​		索引维护
>
> ​				由于B+树涉及到保证数据的有序性，所以在插入时涉及数据的移动，若当前数据页已满，会出现页分裂的现象
>
> ​				页分裂会影响性能
>
> ​				解决：采用自增主键插入模式，这样不涉及数据移动，以及叶子节点的分裂

> ​		覆盖索引
>
> ​				由于二级索引的特性，致使查询数据时会出现回表
>
> ​				若查询的数据为二级索引叶子节点内的数据，则不需要回表（如主键），这时我们说使用了覆盖索引
>
> ​		联合索引
>
> ​				若一个查询涉及多个索引项进行查询，如

```mysql
mysql> select * from tuser where name like '张%' and age=10 and ismale=1;
```

> ​				此时，我们可以建立一个联合索引，如

```mysql
CREATE TABLE `tuser` (
  `id` int(11) NOT NULL,
  `id_card` varchar(32) DEFAULT NULL,
  `name` varchar(32) DEFAULT NULL,
  `age` int(11) DEFAULT NULL,
  `ismale` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `id_card` (`id_card`),
  KEY `name_age` (`name`,`age`)
) ENGINE=InnoDB
```

> ​				在联合索引中，二级索引的叶子节点中数据涉及了所有的索引字段
>
> ​		最左前缀原则
>
> ​				对于联合索引，MySQL来说，是先按照第一个索引进行排序，在第一索引字段相等时，使用第二索引进行排序，以此类				推，所以，使用联合索引应遵循最左前缀原则，挨个索引进行匹配，若想跳过其中一个索引是不会命中索引的。
>
> ​				如：创建索引 id_name_age,但查询时条件为where id=? and name=?时，仅会命中id的索引，仍需要回表
>
> ​		索引下推
>
> ​				在使用联合索引时，若条件是按照联合索引建立顺序判断字段，判断时会按照联合索引顺序进行筛选，这个过程叫做索引				下推	

##### 普通索引与唯一索引的选择

###### 区别

> ​		在查询方面

```mysql
select name from CUser where id_card = 'xxxxxxxyyyyyyzzzzz';
```

> ​		对于普通索引，因为不是唯一索引，所以在根据条件查询后，需要找下一个记录
>
> ​		对于唯一索引，由于索引对应的数据唯一，所以在根据条件查询后，直接返回即可
>
> ​		但由于数据库查询实际上是将数据页读到内存中，所以上面两类操作对CPU的消耗差不多

> ​		在更新方面
>
> ​		首先介绍一下changeBuffer。
>
> ​		MySQL将更新操作是在内存上进行的，若数据页并没有在内存中，将更新操作记录在changeBuffer中，等下次查询将数据页
>
> ​		读入内存中时，对内存中的数据进行更改（这个操作成为merge），除了每次访问该数据页会出现merge，后台也会定期merge
>
> ​		再有就是在数据库正常关闭时会进行一次merge
>
> ​		changeBuffer的优点：
>
> ​		若是没有changeBuffer这部分的话，更新操作是先由数据库读到内存中，而从数据库读到内存是需要占用buffer pool的，使用
>
> ​		了changeBuffer不仅避免内存过度的占有，提高利用率，同时加快了更新操作的速度
>
> ​	
>
> ​		所以，对于changeBuffer来说，需要在将数据页读入内存中之前尽量在changeBuffer中写的多一些
>
> ​		
>
> ​		说回到唯一索引，由于唯一索引需要保证的数据的唯一性，需要从数据库中读出数据页，判断是否冲突
>
> ​		所以对于唯一索引来说，不使用changeBuffer，因为在更新之前，内存中一定会出现想要更新位置的数据页，故而直接在内存
>
> ​		中修改即可，也就不需要changeBuffer
>
> ​		而对于普通索引，则是采用changeBuffer方式进行更新
>
> ​		
>
> ​		在更新方面，使用普通索引+changeBuffer会对更新操作会对更新操作进行优化

> ​		changeBuffer与redo log
>
> ​		对于上面的描述，我们知道changeBuffer为普通索引的一部分，而redo log是在进行更新时进行的写日志操作
>
> ​		那么对于这样来说，整个更新操作的流程是怎样的
>
> ​		已下面的SQL为例

```mysql
mysql> insert into t(id,k) values(id1,k1),(id2,k2);
```

> ​		我们假设对于k1所在的数据页在内存中，而k2所在的数据页不在内存中

<img src="/Users/didi/Documents/myBook/pic/redo log与changeBuffer.png" alt="redo log与changeBuffer" style="zoom:80%;" />

> ​		对于使用changeBuffer的更新，在记录changeBuffer后，redo log会记录你对于changeBuffer的操作（注意这里记录的是对
>
> ​		于changeBuffer的操作并不是merge后对数据页的操作）

##### 为什么MySQL会选错索引

> ​		首先，通过MySQL的执行流程我们会发现，是优化器根据一些因素来选择索引来执行SQL
>
> ​		而这些因素可能是扫描行数，是否主键索引，是否需要排序等等
>
> ​		所以对于选错索引的原因是因为优化器在抉择索引时出现了因素的异常

> ​		1.扫描行数统计出现异常

```mysql
mysql> select * from t where a between 10000 and 20000;
```

> ​		数据库场景：在a字段设置索引，插入10万行数据

```mysql
set long_query_time=0; /*设置慢查询阈值，此处为0，意味着每个数据均记录在慢查询日志中*/
select * from t where a between 10000 and 20000; /*Q1*/
select * from t force index(a) where a between 10000 and 20000;/*Q2*/
```

![慢查询记录](/Users/didi/Documents/myBook/pic/慢查询记录.png)

> ​		会发现，未强制制定使用的索引时，进行的是全表扫描，优化器未选择a索引进行执行

![优化器预估结果](/Users/didi/Documents/myBook/pic/优化器预估结果.png)

> ​		而使用explain得出优化器对于索引的预估中，对于索引a的扫描行数应该为10000，可预估成了37116，对于扫描行数出现了异
>
> ​		常，又因为a索引每次需要回表，结合以上因素，所以优化器未选择索引a
>
> ​		解决：使用analyze table xxxx来进行重新统计索引信息，进而调整优化器对于索引的判断

> ​		2.由于SQL的逻辑，进而选错索引

```mysql
mysql> explain select * from t where (a between 1 and 1000) and (b between 50000 and 100000) order by b limit 1;
```

![执行计划](/Users/didi/Documents/myBook/pic/执行计划.png)

> ​		对于上面的SQL，我们原本想的是使用a索引，因为这样扫描的行数少，而实际选择了b索引
>
> ​		因为在末尾的order by b limit 1，而优化器认为b有索引，认为不需要排序，进而选择b索引
>
> ​		解决：将后面变为order by b,a limit 1，此处对于b索引来说也需要进行排序了，这时优化器就会选择索引a

> ​		综上，对于选择的索引异常的解决
>
> ​				1.使用force index(xxx) 进行强制制定索引（但这样的做法不太灵活）
>
> ​				2.如果判断索引的扫描行数确实异常，使用analyze table xxx来重新统计索引信息
>
> ​				3.删掉多余索引（防止多余索引对优化器的影响）或者添加索引（创建更合适的索引供优化器选择）

##### 如何给字符串创建索引

> ​		对于长字符串创建索引，由于长字符串的特殊性：字节大
>
> ​		1.创建全索引：会出现节点空间较大的问题
>
> ​		2.创建前缀索引：由于使用全索引的值是唯一的，而对于前缀索引肯定其区分度一定小于全索引，会比全索引多进行几次查询
>
> ​		（由于区分度的原因，所以对于前缀索引一定要考量长度问题）
>
> ​		3.按照倒序索引：如果存储对象像身份证号这样的特征（后几位唯一），使用倒序内容存入并存入索引
>
> ​		4.使用hash字段：创建一个字段，将字符串通过crc32（）函数得出校验码（这个函数算出的校验码重复的概率很低，所以这
>
> ​		个索引的区分度比方式3大，且查询性能稳定）

```mysql
mysql> alter table SUser add index index1(email); /* 全索引*/
或
mysql> alter table SUser add index index2(email(6)); /* 前缀索引*/
```

#### <span id="lock">锁</span>

> ​		MySQL中将锁分为全局锁，表锁，行锁

##### 全局锁

> ​		全局锁就是对数据库整库进行加锁
>
> ​		MySQL提供了全局读锁，命令为Flush tables with read lock（FTWRL），这样数据库对于更改数据库的操作进行阻塞
>
> ​		全局表的典型场景为做全库逻辑备份
>
> ​		说到备份，可以想到在一个隔离事务下拿到一致性视图，所以这是备份可在用可重复读下的隔离事物
>
> ​		官方自带的逻辑备份工具是 mysqldump。当 mysqldump 使用参数–single-transaction 的时候，导数据之前就会启动一个事
>
> ​		务，来确保拿到一致性视图。而由于 MVCC 的支持，这个过程中数据是可以正常更新的。
>
> ​		但这个使用需要数据库对应的引擎支持事务（如：InnoDB）

##### 表级锁

> ​		MySQL中存在两种表级锁，一种是普通的表级锁（针对于DML），另一种是MDL（针对于DDL）
>
> ​		第一种就是对数据处理的一种并发处理方式，语法：lock tables ... read/write，但是这种锁的粒度还是有点大，对于支持行锁
>
> ​		的InnoDB来说，这种并发处理方式并不采用
>
> ​		第二种不是显式的锁，而是在访问一个表时自动加上
>
> ​		如果在你访问数据的时候，而另一个线程对于这个表的结构进行修改，这样致使所读出数据与表结构不一致
>
> ​		这个锁用来避免这个问题的出现
>
> ​		当是访问数据时（增删改查），将其加上MDL读锁，读锁之间不会互斥。
>
> ​		当是修改表结构（DDL），将其加上MDL写锁，写锁之间互斥，读写锁之间互斥

##### 行锁

<img src="/Users/didi/Documents/myBook/pic/行锁.png" alt="行锁" style="zoom:80%;" />

> ​		如上图，事务B的操作会呗阻塞，直到事务A commit
>
> ​		对于MySQL中，行锁是在需要的时候获取，然后等到事务commit后释放
>
> ​		根据这样的设定，所以在一个事务中，我们尽量要让影响并发度的锁放在事务的后面，避免数据库中阻塞时间过长

##### 死锁检测

<img src="/Users/didi/Documents/myBook/pic/死锁检测.png" alt="死锁检测" style="zoom:80%;" />

> ​		对于上图，会出现事务A拿到id=1的行锁，事务B拿到id=2的行锁，进而出现死锁问题

> ​		解决策略：
>
> ​				1.等待超时，通过innodb_lock_wait_timeout来设置超时时间
>
> ​				2.发起死锁检测，回滚死锁中的一个事务，通过设置innodb_deadlock_detect为on（默认为on）开启死锁检测
>
> ​		对于第一种的等待时间，我们是无法接受的
>
> ​		往往采取第二种方案，但若有1000个请求同时修改同一行，线程会被堵住，被堵住的线程会进行死锁判断，但这样进行完会
>
> ​		发现结果并未死锁，而死锁又是一个需要耗费大量cpu资源的操作
>
> ​		那对于这种热点数据行更新的性能问题
>
> ​				1.若是确定不会出现死锁问题，可关闭死锁检测（但治标不治本，不建议）
>
> ​				2.控制并发度，以此来降低死锁检测的成本（比如使用线程池进行限流）

#### <span id="dirty">脏页</span>

> ​		脏页：当内存数据页和磁盘数据页内容不一致时，这个内存页称为脏页
>
> ​		干净页：反之，当内存数据页和磁盘数据页内容一致时，这个内存页称为干净页
>
> ​		当进行操作是，可能会出现突然慢了的时候（仅偶然的时刻），这时进行了刷脏页操作（将内存数据页刷入磁盘数据页）
>
> ​		刷脏页的场景：	
>
> ​				1.在MySQL空闲时
>
> ​				2.在MySQL关闭时
>
> ​				3.在redo log满时
>
> ​				4.在内存不足时
>
> ​		而前两种情况和我们的操作时的性能问题无关，对于刷脏页的性能问题对于后两种情况
>
> ​		而当redo log满时，MySQL停止更新动作，这个是我们不期望的
>
> ​		而当内存满时，需要淘汰内存中的一些数据页，若其中存在脏页，则需要进行刷脏页

> ​		MySQL刷脏页策略
>
> ​		首先，可以通过参数innodb_io_capacity参数知道这个主机的io能力（即全力刷脏页能力）
>
> ​		而具体策略为按照全力刷脏页的百分比进行刷脏页
>
> ​		这个百分比是通过脏页比例以及redo log写盘速度决定的
>
> ​		其中MySQL的脏页比例上限innodb_max_dirty_pages_pct默认为75%
>
> ​		我们需要时刻关注脏页比例，不要接近75%
>
> ​		下面为查看脏页比例的代码

```mysql
mysql> select VARIABLE_VALUE into @a from global_status where VARIABLE_NAME = 'Innodb_buffer_pool_pages_dirty';
select VARIABLE_VALUE into @b from global_status where VARIABLE_NAME = 'Innodb_buffer_pool_pages_total';
select @a/@b;
```



> ​		在刷脏页时，会有一个有趣的策略：在一个查询需要刷一个脏页时，会刷掉旁边的脏页
>
> ​		而控制这个策略为innodb_flush_neighbors参数，在MySQL 8.0后这个位为0，即默认不进行这个策略

#### <span id="count">数据库中对于数据行个数的统计</span>

> ​		InnoDB引擎与MyISAM引擎对于检索数据行个数的处理是不同的
>
> ​		MyISAM引擎有独立的库，在磁盘上记录数据行的个数
>
> ​		而InnoDB对于数据行个数的查询需要进行全表扫描

> ​		而为什么InnoDB不像MyISAM一样采取将数据行个数存储在磁盘中呢
>
> ​		因为InnoDB支持事务，由于MVCC中，不同的事务环境下的count数是不确定的

> ​		MySQL中对于count操作中，优化器仅对count(*)进行优化，也就是说要是使用MySQL函数进行数据行个数查询，最好使用
>
> ​		count(*)

> ​		但是尽管优化了count(*)，但还是进行了全表扫描，所以有以下方案进行优化

> ​		1.在缓存中存储数据库的数据行个数		

<img src="/Users/didi/Documents/myBook/pic/缓存存储count.png" alt="缓存存储count" style="zoom:50%;" />

> ​		但是会出现一个问题：当一个线程增加缓存中的数据行个数，此时另外一个线程读缓存中数据行个数，以及从数据库读数据
>
> ​		会发现行数比实际查出数据个数多一个

> ​		2.使用数据库存储数据库的数据行个数

<img src="/Users/didi/Documents/myBook/pic/截屏2021-08-08 上午12.01.30.png" alt="截屏2021-08-08 上午12.01.30" style="zoom:50%;" />

> ​		使用此方案会解决上面的问题，因为数据库提供了事务的隔离性，所以此时第二个线程读到的认为之前的数据行个数
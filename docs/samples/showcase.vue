<manifest>
{
  "format": 1,
  "id": "example:showcase",
  "version": "1.0.0",
  "name": "组件样例",
  "author": "mcphone",
  "description": "在五个界面缩放下核对绘制、命中与滚动裁剪",
  "icon": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
}
</manifest>

<template>
  <column id="root">
    <row class="header">
      <text class="title">组件样例</text>
      <button @click="close()">关闭</button>
    </row>
    <tab-bar bind="tab" :tabs="[{text:'排版'},{text:'滚动'},{text:'交互'}]" />

    <scroll v-if="tab == 0" class="body">
      <text class="caption">justify：高 36 的 column</text>
      <row class="cells">
        <column class="cell j-start"><text class="chip">始</text></column>
        <column class="cell j-center"><text class="chip">中</text></column>
        <column class="cell j-end"><text class="chip">末</text></column>
        <column class="cell j-between"><text class="chip">上</text><text class="chip">下</text></column>
      </row>

      <text class="caption">align：满宽的 box</text>
      <box class="band a-start"><text class="chip">start</text></box>
      <box class="band a-center"><text class="chip">center</text></box>
      <box class="band a-end"><text class="chip">end</text></box>
      <box class="band a-stretch"><text class="chip">stretch</text></box>

      <text class="caption">溢出：三个 50 宽的按钮排不下，第三个伸出滚动区</text>
      <row class="overflow">
        <button class="wide" @click="count++">一</button>
        <button class="wide" @click="count++">二</button>
        <button class="wide" @click="count++">三</button>
      </row>

      <text class="caption">断行：每一行都完整地待在屏幕里</text>
      <text>界面缩放调到 75% 和 300% 各看一遍：文字不被裁掉半截，按钮点哪儿响哪儿。</text>
      <divider />
      <progress :value="count * 10 > 100 ? 100 : count * 10" />
      <text>点了 {{ count }} 次</text>
    </scroll>

    <column v-else-if="tab == 1" class="body">
      <text class="caption">滚到一半：看不见的项不高亮、点不到</text>
      <list class="items" item-height="14">
        <button v-for="n in 40" @click="picked = n">第 {{ n }} 项</button>
      </list>
      <text>点到第 {{ picked }} 项</text>
    </column>

    <column v-else class="body">
      <text class="caption">上层按钮盖住下层：只触发上层</text>
      <stack>
        <button class="under" @click="under++">下层 {{ under }}</button>
        <button class="over" @click="over++">上层 {{ over }}</button>
      </stack>

      <text class="caption">禁用按钮盖住下层：点它不穿透</text>
      <stack>
        <button class="under" @click="under++">下层 {{ under }}</button>
        <button class="over" :enabled="false" @click="over++">禁用</button>
      </stack>

      <toggle bind="sound" label="开关" />
      <text v-if="sound">开关是开的</text>
      <button @click="count = 0; picked = 0; under = 0; over = 0">全部清零</button>
    </column>
  </column>
</template>

<script>
  state = { tab: 0, count: 0, picked: 0, under: 0, over: 0, sound: true }
</script>

<style>
  #root      { height: fill; padding: 4; gap: 3; background: $screen; }
  .header    { align: center; gap: 4; }
  .title     { grow: 1; color: $title; }
  /* height: 0 + grow 才是「标题与分段下面剩下的高」；写 fill 拿到的是整屏的高，会顶出屏幕 */
  .body      { height: 0; grow: 1; gap: 3; }
  .caption   { color: $subtle; }

  .cells     { gap: 4; }
  .cell      { width: 24; height: 36; background: $button; }
  .j-start   { justify: start; }
  .j-center  { justify: center; }
  .j-end     { justify: end; }
  .j-between { justify: between; }

  .band      { width: fill; background: $button; }
  .a-start   { align: start; }
  .a-center  { align: center; }
  .a-end     { align: end; }
  .a-stretch { align: stretch; }
  .chip      { background: $accent; color: $title; }

  .overflow  { gap: 2; }
  .wide      { width: 50; text-align: center; }

  .items     { height: 100; align: stretch; }

  /* 上层只盖住下层的左半边：右半边露出下层的计数，点左半边它不许变。
     文字靠右两种写法都给：按钮文字编成 text 属性时看 text-align，编成子节点时看 align */
  .under     { width: 110; height: 16; align: end; text-align: right; }
  .over      { width: 50; height: 16; background: $accent; hover-background: $button-hover; }
</style>

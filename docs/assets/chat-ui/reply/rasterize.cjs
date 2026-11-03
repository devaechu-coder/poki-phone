// 只转换已下载的矢量组合；原始 path 不变，sharp 仅是开发工具依赖。
const fs=require('node:fs/promises'),path=require('node:path'),sharp=require('sharp');
(async()=>{
 const root=path.resolve(__dirname,'../../../..');
 const svg=await fs.readFile(path.join(__dirname,'reply.svg'),'utf8');
 const output=path.join(root,'shared/src/main/resources/assets/mcphone/textures/chat/ui/reply.png');
 await sharp(Buffer.from(svg.replaceAll('currentColor','#ffffff'))).resize(96,96).png().toFile(output);
 const preview=process.argv[2];
 if(preview) {
  const background=Buffer.from('<svg width="400" height="200" xmlns="http://www.w3.org/2000/svg"><rect width="200" height="200" fill="#fafafa"/><rect x="200" width="200" height="200" fill="#19182e"/></svg>');
  const black=await sharp(Buffer.from(svg.replaceAll('currentColor','#202020'))).resize(80,80).png().toBuffer();
  const pink=await sharp(Buffer.from(svg.replaceAll('currentColor','#edc8da'))).resize(24,24).png().toBuffer();
  await sharp(background).composite([{input:black,left:60,top:60},{input:pink,left:288,top:88}]).png().toFile(preview);
 }
})().catch(e=>{process.stderr.write(String(e));process.exitCode=1;});

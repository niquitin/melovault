const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const read = p => fs.readFileSync(path.join(__dirname,p),'utf8');
const html = read('index.html');
const script = read('main.js');
const cfgSource = read('config.js');
const changelogSource = read('changelog.js');
const metadataSource = read('update.json');
const pageAssets = { 'index.html':html,'main.js':script,'config.js':cfgSource,'changelog.js':changelogSource,'update.json':metadataSource };
const errors = [];
const check=(condition, message)=>{if(!condition)errors.push(message);};
const group='https://groups.google.com/g/melovault-testers';
const play='https://play.google.com/apps/testing/app.melovault.player.open';
check(html.includes(group)&&html.includes(play),'Missing Google Groups / Google Play tester link');
const telegram = 'https://t.me/melovaultmusicplayer';
const qrPath = path.join(__dirname, 'assets/support/telegram-group-qr.png');
check(html.includes(telegram), 'Missing Telegram support link');
check(html.includes('src="assets/support/telegram-group-qr.png"'), 'Missing Telegram QR display');
check(fs.existsSync(qrPath) && fs.readFileSync(qrPath).subarray(0, 8).equals(Buffer.from('89504e470d0a1a0a', 'hex')), 'Telegram QR PNG is missing or invalid');
check(html.includes('id="latest-announcement"')&&script.includes('latest-announcement'),'Latest release announcement is not rendered');
check(html.includes('14 ngày')&&html.includes('12 người'),'Tester retention / Google policy text missing');
check(!html.includes('>Tải APK<'),'Public APK button label found');
for(const [name,source] of Object.entries(pageAssets)){
  check(!/https?:\/\/[^\s"'<]*\/releases\/download\//i.test(source),name+': public GitHub release asset URL');
  check(!/https?:\/\/[^\s"'<]*\.apk(?:\?|["'<]|$)/i.test(source),name+': public APK URL');
}
const context = {window:{}};
vm.runInNewContext(cfgSource,context,{timeout:1000});
vm.runInNewContext(changelogSource,context,{timeout:1000});
const cfg=context.window.MELOVAULT_RELEASE || {};
const log=context.window.MELOVAULT_CHANGELOG || {};
const release=JSON.parse(metadataSource);
const latest=log.versions?.[0];
check(!cfg.url,'config.js must never advertise APK URL');
check(!release.apkUrl,'update.json must never advertise APK URL');
check(latest && latest.version && latest.versionCode && latest.date,'Latest changelog item must include version, code, date');
if(latest){
  check(cfg.versionName===latest.version+'-open','config.js latest version differs from changelog.js');
  check(release.versionName===cfg.versionName && release.versionCode===latest.versionCode,'update.json does not match latest changelog');
  check(release.releaseDate===latest.date,'Release date does not match changelog');
  check(['new','improved','fixed','test'].some(k=>latest[k]?.length),'Latest release has no description of changes');
}
if(errors.length){ console.error('SITE POLICY FAIL\n'+errors.map(x=>' - '+x).join('\n')); process.exitCode=1; }
else console.log('SITE POLICY PASS: Play onboarding, latest release announcement, no public APK link, release metadata consistent');

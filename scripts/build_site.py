#!/usr/bin/env python3
"""Build committed GitHub Pages HTML using only Python's standard library."""
from pathlib import Path
import sys,json,html,shutil
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'website'))
from content import PAGES,FAQ,REPO,SOURCE
BASE='https://wabbo-hundeluft.github.io/ROCO/'
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs'
APK=REPO+'/releases/download/v2.0.20/ROCO-2.0.20.apk'
NAV={'en':[('','Home'),('how-roco-scores-work','Scores'),('compatibility','Compatibility'),('faq','FAQ')], 'de':[('','Start'),('how-roco-scores-work','Scores'),('compatibility','Kompatibilität'),('faq','FAQ')]}
LABELS={'today':'Body energy and weekly strain on Android','sleep':'Sleep score and sleep stages','coach':'Personal robot AI coach','training':'Weekly training plan','trends':'HRV and personal health trends','journal-question':'Habit journal check-in'}
def url(lang,slug=''):return '/ROCO/'+('de/' if lang=='de' else '')+(slug+'/' if slug else '')
for lang in ('en','de'):
 for slug,variants in PAGES.items():
  c=variants[lang];canonical=BASE+('de/' if lang=='de' else '')+(slug+'/' if slug else '')
  nav=''.join(f'<a href="{url(lang,s)}"'+(' aria-current="page"' if s==slug else '')+f'>{label}</a>' for s,label in NAV[lang])
  gallery='<div class="gallery">'+''.join(f'<figure><a href="/ROCO/assets/{name}.png"><img src="/ROCO/assets/{name}.png" width="427" height="852" loading="lazy" decoding="async" alt="{label}"></a><figcaption>{label}</figcaption></figure>' for name,label in LABELS.items())+'</div>'
  content=c['body']
  if slug=='faq':content='<p class="lead">'+('The current Android preview, explained without hidden prerequisites.' if lang=='en' else 'Die aktuelle Android-Vorschau ohne versteckte Voraussetzungen erklärt.')+'</p>'+''.join(f'<section class="faq-item"><h2>{html.escape(q)}</h2><p>{html.escape(a)}</p></section>' for q,a in FAQ[lang])
  for k,v in {'apk':APK,'repo':REPO,'source':SOURCE,'root':'/ROCO/','local':url(lang),'gallery':gallery}.items():content=content.replace('{'+k+'}',v)
  schema={'@context':'https://schema.org','@type':'MobileApplication' if not slug else 'WebPage','name':'ROCO' if not slug else c['title'],'url':canonical,'description':c['description'],'inLanguage':lang}
  if not slug:schema.update(operatingSystem='Android 9+',applicationCategory='HealthApplication',softwareVersion='2.0.20',license=REPO+'/blob/main/LICENSE',downloadUrl=APK,codeRepository=REPO,image=BASE+'assets/social.png',author={'@type':'Person','name':'wabbo-hundeluft','url':'https://github.com/wabbo-hundeluft'})
  switch=url('de' if lang=='en' else 'en',slug)
  footer='Independent wellness estimates. Not affiliated with Google, WHOOP or Garmin. Not a medical device.' if lang=='en' else 'Unabhängige Wellness-Schätzungen. Keine Verbindung zu Google, WHOOP oder Garmin. Kein Medizinprodukt.'
  text=f'''<!doctype html>
<html lang="{lang}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(c['title'])}</title><meta name="description" content="{html.escape(c['description'],quote=True)}"><meta name="robots" content="index,follow,max-image-preview:large">
<link rel="canonical" href="{canonical}"><link rel="alternate" hreflang="en" href="{BASE}{slug+'/' if slug else ''}"><link rel="alternate" hreflang="de" href="{BASE}de/{slug+'/' if slug else ''}"><link rel="alternate" hreflang="x-default" href="{BASE}{slug+'/' if slug else ''}">
<meta property="og:type" content="website"><meta property="og:title" content="{html.escape(c['title'],quote=True)}"><meta property="og:description" content="{html.escape(c['description'],quote=True)}"><meta property="og:url" content="{canonical}"><meta property="og:image" content="{BASE}assets/social.png"><meta property="og:image:width" content="1200"><meta property="og:image:height" content="630"><meta property="og:image:alt" content="ROCO robot and real Android app screens with synthetic demo data"><meta property="og:locale" content="{'en_US' if lang=='en' else 'de_DE'}"><meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="/ROCO/assets/roco.png"><link rel="stylesheet" href="/ROCO/assets/site.css"><script type="application/ld+json">{json.dumps(schema,ensure_ascii=False).replace('</','<\\/')}</script></head>
<body><a class="skip" href="#main">{'Skip to content' if lang=='en' else 'Zum Inhalt'}</a><header><a class="brand" href="{url(lang)}"><img src="/ROCO/assets/roco.png" alt="" width="38" height="38">ROCO<span class="preview">PREVIEW</span></a><nav aria-label="{'Main' if lang=='en' else 'Hauptnavigation'}">{nav}<a href="{switch}" lang="{'de' if lang=='en' else 'en'}">{'Deutsch' if lang=='en' else 'English'}</a></nav></header>
<main id="main" class="{'home' if not slug else 'guide'}"><div class="eyebrow">{'YOUR WATCH. YOUR CONTEXT.' if lang=='en' else 'DEINE UHR. DEIN KONTEXT.'}</div><h1>{html.escape(c['h1'])}</h1>{content}<section class="closing"><h2>{'Curious? Start with your data.' if lang=='en' else 'Neugierig? Starte mit deinen Daten.'}</h2><div class="actions"><a class="button" href="{APK}">{'Try the Android preview' if lang=='en' else 'Android-Vorschau ausprobieren'} ↗</a><a class="button secondary" href="{REPO}">{'View source' if lang=='en' else 'Quellcode ansehen'}</a></div><p class="small">{'Development-signed APK · Check eligibility before connecting Google Health.' if lang=='en' else 'APK mit Entwicklungszertifikat · Google-Health-Zugang vorher prüfen.'}</p></section></main>
<footer><p>{footer}</p><div><a href="{url(lang,'privacy')}">{'Privacy' if lang=='en' else 'Datenschutz'}</a><a href="{url(lang,'whoop-alternative-pixel-watch')}">{'WHOOP comparison' if lang=='en' else 'WHOOP-Vergleich'}</a><a href="{url(lang,'pixel-watch-recovery-score')}">{'Pixel Watch guide' if lang=='en' else 'Pixel-Watch-Leitfaden'}</a><a href="{REPO}/releases">Releases</a><a href="{REPO}/issues">Feedback</a><a href="{REPO}/blob/main/LICENSE">Apache 2.0</a><a href="https://github.com/wabbo-hundeluft">wabbo-hundeluft</a></div><p class="small">{'Content reviewed' if lang=='en' else 'Inhalt geprüft'}: <time datetime="2026-10-29">2026-10-29</time>. {'Website adds no analytics or tracking scripts.' if lang=='en' else 'Website ohne zusätzliche Analyse- oder Tracking-Skripte.'}</p></footer></body></html>'''
  target=OUT/('de' if lang=='de' else '')/slug/'index.html';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(text)
(OUT/'.nojekyll').touch()
urls=[BASE+('de/' if lang=='de' else '')+(s+'/' if s else '') for lang in ('en','de') for s in PAGES]
(OUT/'sitemap.xml').write_text('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">'+''.join('<url><loc>'+u+'</loc><lastmod>2026-10-29</lastmod></url>' for u in urls)+'</urlset>\n')
# A project subdirectory cannot set origin-level robots policy. This file is informational.
(OUT/'robots.txt').write_text('# GitHub project Pages: effective robots.txt must be at the HOST root, not /ROCO/.\nUser-agent: *\nAllow: /\nSitemap: '+BASE+'sitemap.xml\n')
(OUT/'404.html').write_text('<!doctype html><html lang="en"><meta charset="utf-8"><meta name="robots" content="noindex"><title>Page not found — ROCO</title><h1>Page not found</h1><p><a href="/ROCO/">ROCO home</a></p></html>')
print(f'Built {len(urls)} static EN/DE pages')

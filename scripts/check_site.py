#!/usr/bin/env python3
"""Checks actual HTML output: links, SEO metadata, structured data and hreflang."""
from pathlib import Path
from html.parser import HTMLParser
from urllib.parse import urlparse,unquote
import json,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];DOCS=ROOT/'docs';BASE='https://wabbo-hundeluft.github.io/ROCO/'
class Page(HTMLParser):
 def __init__(self):super().__init__();self.h1=0;self.title='';self.description='';self.canonical='';self.links=[];self.alts={};self.json=[];self.in_title=False;self.in_json=False;self.lang='';self.bad_images=[]
 def handle_starttag(self,t,a):
  d=dict(a)
  if t=='html':self.lang=d.get('lang','')
  if t=='h1':self.h1+=1
  if t=='title':self.in_title=True
  if t=='meta' and d.get('name')=='description':self.description=d.get('content','')
  if t=='link' and d.get('rel')=='canonical':self.canonical=d.get('href','')
  if t=='link' and d.get('rel')=='alternate':self.alts[d.get('hreflang')]=d.get('href','')
  for k in ('href','src'):
   if k in d:self.links.append(d[k])
  if t=='img' and 'alt' not in d:self.bad_images.append(d)
  if t=='script':
   assert d.get('type')=='application/ld+json','Unexpected script'
   self.in_json=True;self.json.append('')
 def handle_data(self,s):
  if self.in_title:self.title+=s
  if self.in_json:self.json[-1]+=s
 def handle_endtag(self,t):
  if t=='title':self.in_title=False
  if t=='script':self.in_json=False
pages={};titles=set();descriptions=set()
for f in DOCS.rglob('index.html'):
 p=Page();p.feed(f.read_text());rel=str(f.relative_to(DOCS).parent);rel='' if rel=='.' else rel+'/'
 assert p.h1==1 and p.lang in ('en','de'),f
 assert p.title and p.description and p.title not in titles and p.description not in descriptions,f
 titles.add(p.title);descriptions.add(p.description)
 assert p.canonical==BASE+rel,(f,p.canonical)
 assert set(p.alts)=={'en','de','x-default'},f
 assert not p.bad_images,f
 for s in p.json:
  j=json.loads(s);assert 'aggregateRating' not in j and 'review' not in j and 'offers' not in j
 for link in p.links+list(p.alts.values()):
  u=urlparse(link)
  if u.netloc and u.netloc!='wabbo-hundeluft.github.io':continue
  if not u.path or u.path.startswith('#'):continue
  assert u.path.startswith('/ROCO/'),(f,link)
  target=DOCS/unquote(u.path[len('/ROCO/'):]);target=target/'index.html' if target.is_dir() or u.path.endswith('/') else target
  assert target.exists(),(f,link)
 pages[p.canonical]=p
for canonical,p in pages.items():
 for lang,target in p.alts.items():
  assert target in pages,(canonical,target)
  assert pages[target].alts.get(p.lang)==canonical,(canonical,target,'not reciprocal')
s=ET.parse(DOCS/'sitemap.xml');urls={x.text for x in s.findall('.//{http://www.sitemaps.org/schemas/sitemap/0.9}loc')};assert urls==set(pages)
assert len(pages)==14
print('PASS: 14 EN/DE pages; unique titles/descriptions; canonical/hreflang reciprocity; local links/images; JSON-LD; sitemap parity; no tracking scripts')

"""Check responsive widget sizing and the source contracts preventing stretched cards."""
from pathlib import Path
import argparse
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def check_source_contracts():
    cards = (ROOT / 'port/WeatherCardWidgetRenderer.java').read_text(encoding='utf-8')
    assert 'canvas.scale(width/w,height/h)' not in cards
    assert 'float scale=Math.min(width/w,height/h);' in cards
    assert 'sky.drawSky(canvas,data,time,width,height);' in cards
    sky = (ROOT / 'port/WeatherWidgetRenderer.java').read_text(encoding='utf-8')
    assert 'private void assetCrop(' in sky
    assert 'Math.max(w / bitmap.getWidth(), h / bitmap.getHeight())' in sky
    oxygen = (ROOT / 'port/WeatherOxygenBackground.java').read_text(encoding='utf-8')
    assert 'Standard TodayWeatherWidget uses ImageView.fitXY' not in oxygen
    assert 'float scale=Math.max(width/combined.getWidth(),height/combined.getHeight());' in oxygen
    layout = (ROOT / 'port/res/layout/weather_widget.xml').read_text(encoding='utf-8')
    assert 'android:scaleType="fitCenter"' in layout and 'fitXY' not in layout
    provider = (ROOT / 'port/WeatherWidgetProvider.java').read_text(encoding='utf-8')
    assert re.search(r'onAppWidgetOptionsChanged\([^)]*Bundle options\)\s*\{.*?readSnapshot\(context\),options\);',
                     provider, re.DOTALL)
    print('PASS: responsive renderer, proportional sky, fitCenter and latest-options source contracts')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, default=os.environ.get('JAVA_HOME'))
    args = parser.parse_args()
    if args.java_home is None:
        parser.error('set JAVA_HOME or pass --java-home')
    suffix = '.exe' if os.name == 'nt' else ''
    javac = args.java_home / 'bin' / ('javac' + suffix)
    java = args.java_home / 'bin' / ('java' + suffix)
    if not javac.is_file() or not java.is_file():
        parser.error('JDK java/javac not found below ' + str(args.java_home))
    check_source_contracts()
    catalog = (ROOT / 'port/WeatherWidgetCatalog.java').read_text(encoding='utf-8')
    providers = re.findall(r'(Weather\w*Provider)\.class', catalog)
    assert len(providers) == 25
    sources = {
        'android/appwidget/AppWidgetManager.java': '''package android.appwidget;
public class AppWidgetManager {
 public static final String OPTION_APPWIDGET_SIZES="sizes";
 public static final String OPTION_APPWIDGET_MIN_WIDTH="minWidth", OPTION_APPWIDGET_MAX_WIDTH="maxWidth";
 public static final String OPTION_APPWIDGET_MIN_HEIGHT="minHeight", OPTION_APPWIDGET_MAX_HEIGHT="maxHeight";
}''',
        'android/content/Context.java': '''package android.content;
public class Context { public android.content.res.Resources resources=new android.content.res.Resources();
 public android.content.res.Resources getResources(){return resources;} }''',
        'android/content/res/Resources.java': '''package android.content.res;
public class Resources { public Configuration configuration=new Configuration(); public android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
 public Configuration getConfiguration(){return configuration;} public android.util.DisplayMetrics getDisplayMetrics(){return metrics;} }''',
        'android/content/res/Configuration.java': '''package android.content.res;
public class Configuration { public static final int ORIENTATION_LANDSCAPE=2; public int orientation=1; }''',
        'android/os/Build.java': 'package android.os; public class Build { public static class VERSION { public static int SDK_INT=31; } }',
        'android/os/Bundle.java': '''package android.os;
public class Bundle { public java.util.Map<String,Object> values=new java.util.HashMap<>();
 public <T> T getParcelableArrayList(String key){return (T)values.get(key);}
 public int getInt(String key,int fallback){Object value=values.get(key);return value instanceof Integer?(Integer)value:fallback;}
 public Bundle put(String key,Object value){values.put(key,value);return this;} }''',
        'android/util/DisplayMetrics.java': 'package android.util; public class DisplayMetrics { public float density=1; public int widthPixels=2000,heightPixels=2000; }',
        'android/util/SizeF.java': '''package android.util;
public final class SizeF { private final float w,h; public SizeF(float w,float h){this.w=w;this.h=h;}
 public float getWidth(){return w;} public float getHeight(){return h;}
 public boolean equals(Object o){return o instanceof SizeF && Float.compare(w,((SizeF)o).w)==0 && Float.compare(h,((SizeF)o).h)==0;}
 public int hashCode(){return Float.hashCode(w)*31+Float.hashCode(h);} public String toString(){return w+"x"+h;} }''',
        'dev/weather/oneplus/WeatherWidgetProvidersStubs.java': 'package dev.weather.oneplus;\n' + '\n'.join(
            'class '+name+' {}' for name in providers),
        'dev/weather/oneplus/WidgetResponsiveAudit.java': '''package dev.weather.oneplus;
import android.appwidget.AppWidgetManager;import android.content.Context;import android.os.Build;import android.os.Bundle;import android.util.SizeF;import java.util.*;
public class WidgetResponsiveAudit {
 static int checks; static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static WeatherWidgetSizing.Size only(Context c,Bundle b,int style){java.util.List<WeatherWidgetSizing.Size> out=WeatherWidgetSizing.resolve(c,b,style);check(out.size()==1,"fallback size count");return out.get(0);}
 public static void main(String[] args){
  Context c=new Context();Bundle b=new Bundle();
  b.put(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,280).put(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,520)
   .put(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,260).put(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,480);
  Build.VERSION.SDK_INT=30;
  for(int style=0;style<25;style++){
   c.resources.configuration.orientation=1;WeatherWidgetSizing.Size portrait=only(c,b,style);
   check(portrait.width==280&&portrait.height==480,"portrait min width/max height "+style);
   c.resources.configuration.orientation=2;WeatherWidgetSizing.Size land=only(c,b,style);
   check(land.width==520&&land.height==260,"landscape max width/min height "+style);
  }
  Build.VERSION.SDK_INT=31;ArrayList<SizeF> supplied=new ArrayList<>();supplied.add(new SizeF(500,300));supplied.add(new SizeF(300,500));
  b.put(AppWidgetManager.OPTION_APPWIDGET_SIZES,supplied);
  for(int style=0;style<25;style++){
   java.util.List<WeatherWidgetSizing.Size> both=WeatherWidgetSizing.resolve(c,b,style);
   check(both.size()==2&&both.get(0).width==500&&both.get(0).height==300&&both.get(1).width==300&&both.get(1).height==500,"responsive variants "+style);
  }
  c.resources.metrics.density=2;c.resources.metrics.widthPixels=500;c.resources.metrics.heightPixels=500;
  for(int style=0;style<25;style++){
   java.util.List<WeatherWidgetSizing.Size> both=WeatherWidgetSizing.resolve(c,b,style);
   float wide=both.get(0).width/(float)both.get(0).height,tall=both.get(1).width/(float)both.get(1).height;
   check(Math.abs(wide-500f/300f)<.01&&Math.abs(tall-300f/500f)<.01,"memory budget aspect "+style);
  }
  supplied.clear();for(int i=0;i<20;i++)supplied.add(new SizeF(100+i,200+i));
  check(WeatherWidgetSizing.resolve(c,b,0).size()==16,"responsive RemoteViews variant limit");
  System.out.println("PASS: "+checks+" sizing checks across all 25 widget styles");
 }
}'''
    }
    (ROOT / '.build').mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='widget-responsive-', dir=ROOT/'.build') as temporary:
        work = Path(temporary)
        files = []
        for relative, content in sources.items():
            target = work / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding='utf-8')
            files.append(target)
        files.extend([ROOT/'port/WeatherWidgetSizing.java', ROOT/'port/WeatherWidgetCatalog.java'])
        base = [str(javac), '-encoding', 'UTF-8', '-d', str(work), *map(str, files)]
        first = subprocess.run([base[0], '--release', '8', *base[1:]], capture_output=True)
        if first.returncode:
            second = subprocess.run([base[0], '-source', '8', '-target', '8', *base[1:]], capture_output=True)
            if second.returncode:
                print(first.stdout.decode('utf-8', 'replace'), first.stderr.decode('utf-8', 'replace'))
                print(second.stdout.decode('utf-8', 'replace'), second.stderr.decode('utf-8', 'replace'))
                second.check_returncode()
        subprocess.run([str(java), '-cp', str(work), 'dev.weather.oneplus.WidgetResponsiveAudit'], check=True)


if __name__ == '__main__':
    main()

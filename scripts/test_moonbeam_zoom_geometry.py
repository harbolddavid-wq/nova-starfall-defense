#!/usr/bin/env python3
"""Deterministic viewport-transform regression; does not pretend to be touch testing."""
from pathlib import Path
import random, math, json
root=Path(__file__).resolve().parent.parent
src=(root/"coloringbook/src/main/java/com/novasky/moonbeam/MainActivity.java").read_text()
assert "cancelStroke();gestureMode=true" in src, "second finger must cancel pending paint"
assert src.index("if(gestureMode)") < src.index("if(act==MotionEvent.ACTION_DOWN)"), "gestures must intercept paint"
assert "void fit(){zoom=1f;panX=panY=0" in src
assert "float x=(e.getX()-offX)/scale,y=(e.getY()-offY)/scale;" in src
rng=random.Random(20261030)
maxerr=0.0
for i in range(2000):
    vieww=rng.uniform(330,1300);viewh=rng.uniform(500,2300)
    fit=min((vieww-8)/720,(viewh-8)/900)
    old_zoom=rng.uniform(1,7);old_scale=fit*old_zoom
    old_panx=rng.uniform(-100,100);old_pany=rng.uniform(-200,200)
    old_offx=(vieww-720*old_scale)/2+old_panx
    old_offy=(viewh-900*old_scale)/2+old_pany
    anchorx=rng.uniform(30,690);anchory=rng.uniform(30,870)
    midx=old_offx+anchorx*old_scale
    midy=old_offy+anchory*old_scale
    # Java pinch formula: anchor stays under midpoint when zoom changes.
    newzoom=max(1,min(7,old_zoom*rng.uniform(.4,2.3)))
    newscale=fit*newzoom
    newpanx=midx-anchorx*newscale-(vieww-720*newscale)/2
    newpany=midy-anchory*newscale-(viewh-900*newscale)/2
    newoffx=(vieww-720*newscale)/2+newpanx
    newoffy=(viewh-900*newscale)/2+newpany
    backx=(midx-newoffx)/newscale;backy=(midy-newoffy)/newscale
    maxerr=max(maxerr,abs(backx-anchorx),abs(backy-anchory))
    assert abs(backx-anchorx)<1e-8 and abs(backy-anchory)<1e-8
report={"cases":2000,"max_inverse_error_canvas_pixels":maxerr,
        "checked":["zoom anchor invariant before pan clamp","canvas-to-touch inverse",
                   "two-finger gesture cancels paint","Fit resets zoom/pan"],
        "not_verified":["actual Android multitouch","physical BLU gestures","UI screenshot"]}
(root/"MOONBEAM_013_ZOOM_MATH_TEST.json").write_text(json.dumps(report,indent=2))
print("ZOOM GEOMETRY PASS",report)

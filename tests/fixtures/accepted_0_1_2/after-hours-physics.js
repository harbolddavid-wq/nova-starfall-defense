/* Shared aiming and collision geometry. Coordinates are in 960x540 game units. */
(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.AfterHoursPhysics = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";
  const GRAVITY = 820, PULL_LIMIT = 108, SPEED = 11;

  function pull(anchor, dx, dy) {
    dx = Math.min(0, dx);
    const length = Math.hypot(dx, dy);
    if (length > PULL_LIMIT) { dx *= PULL_LIMIT / length; dy *= PULL_LIMIT / length; }
    return { x: anchor.x + dx, y: anchor.y + dy };
  }
  function velocity(anchor, pos) {
    return { x: (anchor.x - pos.x) * SPEED, y: (anchor.y - pos.y) * SPEED };
  }
  function pointAt(pos, v, t) {
    return { x: pos.x + v.x * t, y: pos.y + v.y * t + .5 * GRAVITY * t * t };
  }
  function move(p, dt) {
    p.x += p.vx * dt;
    p.y += p.vy * dt + .5 * GRAVITY * dt * dt;
    p.vy += GRAVITY * dt;
  }
  // Sweep a circle center against an expanded rectangle. Returns earliest contact
  // and its outward normal, so fast projectiles cannot skip thin structures.
  function sweepBox(x, y, dx, dy, radius, b) {
    const left = b.x - b.w / 2 - radius, right = b.x + b.w / 2 + radius;
    const top = b.y - b.h / 2 - radius, bottom = b.y + b.h / 2 + radius;
    let enter = -Infinity, exit = Infinity, nx = 0, ny = 0;
    for (const [p, d, lo, hi, ax, ay] of [[x,dx,left,right,1,0],[y,dy,top,bottom,0,1]]) {
      if (Math.abs(d) < 1e-12) { if (p < lo || p > hi) return null; continue; }
      let a = (lo-p)/d, z = (hi-p)/d, sign = -1;
      if (a > z) { [a,z] = [z,a]; sign = 1; }
      if (a > enter) { enter = a; nx = sign*ax; ny = sign*ay; }
      exit = Math.min(exit,z);
      if (enter > exit) return null;
    }
    if (exit < 0 || enter > 1) return null;
    if (enter < 0) {
      // Already overlapping: choose the shallowest way out rather than inventing
      // a zero normal; caller applies per-body cooldown after a collision.
      const choices = [[x-left,-1,0],[right-x,1,0],[y-top,0,-1],[bottom-y,0,1]];
      choices.sort((a,b)=>a[0]-b[0]); nx=choices[0][1]; ny=choices[0][2];
    }
    return {t:Math.max(0,enter), nx, ny};
  }
  function sweepCircle(x,y,dx,dy,cx,cy,r) {
    const ox=x-cx, oy=y-cy, a=dx*dx+dy*dy, c=ox*ox+oy*oy-r*r;
    if (c<=0) return 0;
    if (a<1e-12) return null;
    const b=2*(ox*dx+oy*dy), disc=b*b-4*a*c;
    if (disc<0) return null;
    const t=(-b-Math.sqrt(disc))/(2*a);
    return t>=0 && t<=1 ? t : null;
  }
  function reflect(vx,vy,nx,ny,restitution) {
    const vn=vx*nx+vy*ny;
    return vn < 0 ? {x:vx-(1+restitution)*vn*nx,y:vy-(1+restitution)*vn*ny} : {x:vx,y:vy};
  }
  return { GRAVITY, PULL_LIMIT, SPEED, pull, velocity, pointAt, move, sweepBox, sweepCircle, reflect };
});

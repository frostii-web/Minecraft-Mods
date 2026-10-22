package dev.frostii.irg.client;

import java.util.*;

/** Original orthographic cube projection. Rendering and picking use identical world-space face polygons. */
public final class CubeGeometry {
    private CubeGeometry() { }
    public record Face(int direction, double[][] points, double depth) {
        public boolean contains(double x, double y) {
            double sign = 0;
            for (int i = 0; i < 4; i++) {
                double[] a = points[i], b = points[(i + 1) % 4];
                double cross = (b[0]-a[0])*(y-a[1])-(b[1]-a[1])*(x-a[0]);
                if (Math.abs(cross) < 1e-7) continue;
                if (sign != 0 && sign * cross < 0) return false;
                sign = cross;
            }
            return true;
        }
    }
    // Direction ordinals: DOWN, UP, NORTH, SOUTH, WEST, EAST. UV order is consistent per face.
    private static final double[][][] VERTICES = {
        {{-.5,-.5,-.5},{.5,-.5,-.5},{.5,-.5,.5},{-.5,-.5,.5}},
        {{-.5,.5,.5},{.5,.5,.5},{.5,.5,-.5},{-.5,.5,-.5}},
        {{.5,.5,-.5},{.5,-.5,-.5},{-.5,-.5,-.5},{-.5,.5,-.5}},
        {{-.5,.5,.5},{-.5,-.5,.5},{.5,-.5,.5},{.5,.5,.5}},
        {{-.5,.5,-.5},{-.5,-.5,-.5},{-.5,-.5,.5},{-.5,.5,.5}},
        {{.5,.5,.5},{.5,-.5,.5},{.5,-.5,-.5},{.5,.5,-.5}}
    };
    private static final double[][] NORMALS = {{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
    private static double[] rotate(double[] v,double yaw,double pitch) {
        double x=Math.cos(yaw)*v[0]+Math.sin(yaw)*v[2];
        double z=-Math.sin(yaw)*v[0]+Math.cos(yaw)*v[2];
        return new double[]{x,Math.cos(pitch)*v[1]-Math.sin(pitch)*z,Math.sin(pitch)*v[1]+Math.cos(pitch)*z};
    }
    public static List<Face> faces(double yaw,double pitch,double cx,double cy,double scale) {
        List<Face> faces=new ArrayList<>();
        for(int direction=0;direction<6;direction++) {
            if(rotate(NORMALS[direction],yaw,pitch)[2]<=1e-7) continue;
            double[][] points=new double[4][]; double depth=0;
            for(int i=0;i<4;i++) { double[] p=rotate(VERTICES[direction][i],yaw,pitch); points[i]=new double[]{cx+p[0]*scale,cy-p[1]*scale}; depth+=p[2]; }
            faces.add(new Face(direction,points,depth/4));
        }
        faces.sort(Comparator.comparingDouble(Face::depth)); return faces;
    }
    public static int pick(List<Face> faces,double x,double y) {
        for(int i=faces.size()-1;i>=0;i--) if(faces.get(i).contains(x,y)) return faces.get(i).direction;
        return -1;
    }
}

/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.graphics.g3d.utils;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectMap.Entry;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pool.Poolable;

/** Base class for applying one or more {@link Animation}s to a {@link ModelInstance}. This class only applies the actual
 * {@link Node} transformations, it does not manage animations or keep track of animation states. See {@link AnimationController}
 * for an implementation of this class which does manage animations.
 * 
 * @author Xoppa */
public class BaseAnimationController {
	public final static class Transform implements Poolable {
		public final Vector3 translation = new Vector3();
		public final Quaternion rotation = new Quaternion();
		public final Vector3 scale = new Vector3(1, 1, 1);

		public Transform () {
		}

		public Transform idt () {
			translation.set(0, 0, 0);
			rotation.idt();
			scale.set(1, 1, 1);
			return this;
		}

		public Transform set (final Vector3 t, final Quaternion r, final Vector3 s) {
			translation.set(t);
			rotation.set(r);
			scale.set(s);
			return this;
		}

		public Transform set (final Transform other) {
			return set(other.translation, other.rotation, other.scale);
		}

		public Transform lerp (final Transform target, final float alpha) {
			return lerp(target.translation, target.rotation, target.scale, alpha);
		}

		public Transform lerp (final Vector3 targetT, final Quaternion targetR, final Vector3 targetS, final float alpha) {
			translation.lerp(targetT, alpha);
			rotation.slerp(targetR, alpha);
			scale.lerp(targetS, alpha);
			return this;
		}

		public Matrix4 toMatrix4 (final Matrix4 out) {
			return out.set(translation, rotation, scale);
		}

		@Override
		public void reset () {
			idt();
		}

		@Override
		public String toString () {
			return translation.toString() + " - " + rotation.toString() + " - " + scale.toString();
		}
	}

	private final Pool<Transform> transformPool = new Pool<Transform>() {
		@Override
		protected Transform newObject () {
			return new Transform();
		}
	};
	private final static ObjectMap<Node, Transform> transforms = new ObjectMap<Node, Transform>();
	private boolean applying = false;
	/** The {@link ModelInstance} on which the animations are being performed. */
	public final ModelInstance target;

	/** Construct a new BaseAnimationController.
	 * @param target The {@link ModelInstance} on which the animations are being performed. */
	public BaseAnimationController (final ModelInstance target) {
		this.target = target;
	}

	/** Begin applying multiple animations to the instance, must followed by one or more calls to {
	 * {@link #apply(Animation, float, float)} and finally {{@link #end()}. */
	protected void begin () {
		if (applying) throw new GdxRuntimeException("You must call end() after each call to being()");
		applying = true;
	}

	/** Apply an animation, must be called between {{@link #begin()} and {{@link #end()}.
	 * @param weight The blend weight of this animation relative to the previous applied animations. */
	protected void apply (final Animation animation, final float time, final float weight) {
		if (!applying) throw new GdxRuntimeException("You must call begin() before adding an animation");
		applyAnimation(transforms, transformPool, weight, animation, time);
	}

	/** End applying multiple animations to the instance and update it to reflect the changes. */
	protected void end () {
		if (!applying) throw new GdxRuntimeException("You must call begin() first");
		for (Entry<Node, Transform> entry : transforms.entries()) {
			entry.value.toMatrix4(entry.key.localTransform);
			transformPool.free(entry.value);
		}
		transforms.clear();
		target.calculateTransforms();
		applying = false;
	}

	/** Apply a single animation to the {@link ModelInstance} and update the it to reflect the changes. */
	protected void applyAnimation (final Animation animation, final float time) {
		if (applying) throw new GdxRuntimeException("Call end() first");
		applyAnimation(null, null, 1.f, animation, time);
		target.calculateTransforms();
	}

	/** Apply two animations, blending the second onto to first using weight. */
	protected void applyAnimations (final Animation anim1, final float time1, final Animation anim2, final float time2,
		final float weight) {
		if (anim2 == null || weight == 0.f)
			applyAnimation(anim1, time1);
		else if (anim1 == null || weight == 1.f)
			applyAnimation(anim2, time2);
		else if (applying)
			throw new GdxRuntimeException("Call end() first");
		else {
			begin();
			apply(anim1, time1, 1.f);
			apply(anim2, time2, weight);
			end();
		}
	}

	private final static Transform tmpT = new Transform();
	private final static Quaternion tmpQ = new Quaternion();
	private final static Vector3 tmpV = new Vector3();

	private final static int getFirstKeyframeIndexAtTime (final float[] times, final float time) {
		final int last = times.length - 1;
		for (int i = 0; i < last; i++) {
			if (time >= times[i] && time <= times[i + 1]) {
				return i;
			}
		}
		return 0;
	}

	private final static Vector3 getVec3(final float[] data, final int base, final Vector3 out) {
		out.x = data[base + 0];
		out.y = data[base + 1];
		out.z = data[base + 2];
		return out;
	}

	private final static Quaternion getQuat(final float[] data, final int base, final Quaternion out) {
		out.x = data[base + 0];
		out.y = data[base + 1];
		out.z = data[base + 2];
		out.w = data[base + 3];
		return out;
	}

	/** If out == null, applies the transform directly to the node. Otherwise interpolates with alpha the value in out, allocating from the pool if necessary **/
	private final static void applyTransform(final Node node, final Transform transform, final ObjectMap<Node, Transform> out,
									  final Pool<Transform> pool, final float alpha) {
		if (out == null) {
			transform.toMatrix4(node.localTransform);
			return;
		}

		Transform t = out.get(node, null);
		if (t != null) {
			if (alpha > 0.999999f)
				t.set(transform);
			else
				t.lerp(transform, alpha);
		} else {
			if (alpha > 0.999999f)
				out.put(node, pool.obtain().set(transform));
			else
				out.put(node, pool.obtain().set(node.translation, node.rotation, node.scale).lerp(transform, alpha));
		}

	}

	/** Applies an animation to either an objectmap or directly to the bones. **/
	private final static void applyAnimationBlending (final Animation anim, final ObjectMap<Node, Transform> out,
		final Pool<Transform> pool, final float alpha, final float time) {
		final Transform transform = tmpT;

		Node[] nodes = anim.nodes;
		int[] formats = anim.formats;
		float[] data = anim.data;

		// degenerate cases
		if (nodes.length == 0) return;
		if (nodes.length == 1) {
			Node node = nodes[0];

			if (formats[0] >= 0) getVec3(data, formats[0], transform.translation);
			else transform.translation.set(node.translation);
			if (formats[1] >= 0) getQuat(data, formats[1], transform.rotation);
			else transform.rotation.set(node.rotation);
			if (formats[2] >= 0) getVec3(data, formats[2], transform.scale);
			else transform.scale.set(node.scale);

			applyTransform(node, transform, out, pool, alpha);
			node.isAnimated = true;

			return;
		}

		// find the current keyframes, lerp position, and buffer positions.
		// frameIdx is guaranteed not to be the last keyframe, so we will always have a lerp target.
		int frameIdx = getFirstKeyframeIndexAtTime(anim.times, time);
		float z = (time - anim.times[frameIdx]) / (anim.times[frameIdx + 1] - anim.times[frameIdx]);
		int currPos = frameIdx * anim.stride;
		int nextPos = currPos + anim.stride;

		for (int c = 0, base = 0, n = nodes.length; c < n; c++, base += 3) {
			Node node = nodes[c];
			int to = formats[base + 0];
			int ro = formats[base + 1];
			int so = formats[base + 2];

			if (to >= 0) {
				getVec3(data, currPos + to, transform.translation);
				getVec3(data, nextPos + to, tmpV);
				transform.translation.lerp(tmpV, z);
			}
			if (ro >= 0) {
				getQuat(data, currPos + ro, transform.rotation);
				getQuat(data, nextPos + ro, tmpQ);
				transform.rotation.slerp(tmpQ, z);
			}
			if (so >= 0) {
				getVec3(data, currPos + so, transform.scale);
				getVec3(data, nextPos + so, tmpV);
				transform.scale.lerp(tmpV, z);
			}

			applyTransform(node, transform, out, pool, alpha);
			node.isAnimated = true;
		}
	}

	/** Helper method to apply one animation to either an objectmap for blending or directly to the bones. */
	protected static void applyAnimation (final ObjectMap<Node, Transform> out, final Pool<Transform> pool, final float alpha,
		final Animation animation, final float time) {

		boolean blend = out != null;

		if (blend) {
			for (final Node node : out.keys())
				node.isAnimated = false;
		}

		applyAnimationBlending(animation, out, pool, alpha, time);

		if (blend) {
			for (final ObjectMap.Entry<Node, Transform> e : out.entries()) {
				if (!e.key.isAnimated) {
					e.key.isAnimated = true;
					e.value.lerp(e.key.translation, e.key.rotation, e.key.scale, alpha);
				}
			}
		}
	}

	/** Remove the specified animation, by marking the affected nodes as not animated. When switching animation, this should be call
	 * prior to applyAnimation(s). */
	protected void removeAnimation (final Animation animation) {
		for (Node node : animation.nodes) {
			node.isAnimated = false;
		}
	}
}

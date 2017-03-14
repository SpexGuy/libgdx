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
import com.badlogic.gdx.graphics.g3d.model.NodeAnimation;
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

	private final static int getFirstKeyframeIndexAtTime (final float[] times, final int baseIdx, final int count, final float time) {
		final int last = baseIdx + count - 1;
		for (int i = baseIdx; i < last; i++) {
			if (time >= times[i] && time <= times[i + 1]) {
				return i;
			}
		}
		return 0;
	}

	private final static Vector3 getVec3(final float[] data, final int idx, final Vector3 out) {
		int base = idx << 2;
		out.x = data[base + 0];
		out.y = data[base + 1];
		out.z = data[base + 2];
		return out;
	}

	private final static Quaternion getQuat(final float[] data, final int idx, final Quaternion out) {
		int base = idx << 2;
		out.x = data[base + 0];
		out.y = data[base + 1];
		out.z = data[base + 2];
		out.w = data[base + 3];
		return out;
	}

	private final static Vector3 getVec3AtTime(final int offset, final int count, final float[] times, final float[] data, float time, final Vector3 out) {
		int index = getFirstKeyframeIndexAtTime(times, offset, count, time);
		out.x = data[index + 0];
		out.y = data[index + 1];
		out.z = data[index + 2];

		if (++index < offset + count) {
			// interpolate to a second keyframe
			float startTime = times[index-1];
			float endTime = times[index];
			final float t = (time - startTime) / (endTime - startTime);
			out.x += t * (data[index + 0] - out.x);
			out.y += t * (data[index + 1] - out.y);
			out.z += t * (data[index + 2] - out.z);
		}
		return out;
	}

	private final static Vector3 getTranslationAtTime (final NodeAnimation nodeAnim, final float time, final Vector3 out) {
		int offset = nodeAnim.translationOffset;
		int count = nodeAnim.translationCount;
		float[] times = nodeAnim.times;
		float[] data = nodeAnim.data;

		if (offset < 0) return out.set(nodeAnim.node.translation);
		if (count == 1) return getVec3(data, offset, out);

		return getVec3AtTime(offset, count, times, data, time, out);
	}

	private final static Vector3 getScalingAtTime (final NodeAnimation nodeAnim, final float time, final Vector3 out) {
		int offset = nodeAnim.scaleOffset;
		int count = nodeAnim.scaleCount;
		float[] times = nodeAnim.times;
		float[] data = nodeAnim.data;

		if (offset < 0) return out.set(nodeAnim.node.scale);
		if (count == 1) return getVec3(data, offset, out);

		return getVec3AtTime(offset, count, times, data, time, out);
	}

	private final static Quaternion getRotationAtTime (final NodeAnimation nodeAnim, final float time, final Quaternion out) {
		int offset = nodeAnim.rotationOffset;
		int count = nodeAnim.rotationCount;
		float[] times = nodeAnim.times;
		float[] data = nodeAnim.data;

		if (offset < 0) return out.set(nodeAnim.node.rotation);
		if (count == 1) return getQuat(data, offset, out);

		int index = getFirstKeyframeIndexAtTime(times, offset, count, time);
		getQuat(data, index, out);

		if (++index < offset + count) {
			// interpolate to a second keyframe
			float startTime = times[index-1];
			float endTime = times[index];
			final float t = (time - startTime) / (endTime - startTime);
			getQuat(data, index, tmpQ);
			out.slerp(tmpQ, t);
		}
		return out;
	}

	private final static Transform getNodeAnimationTransform (final NodeAnimation nodeAnim, final float time) {
		final Transform transform = tmpT;
		getTranslationAtTime(nodeAnim, time, transform.translation);
		getScalingAtTime(nodeAnim, time, transform.scale);
		getRotationAtTime(nodeAnim, time, transform.rotation);
		return transform;
	}

	private final static void applyNodeAnimationDirectly (final NodeAnimation nodeAnim, final float time) {
		final Node node = nodeAnim.node;
		node.isAnimated = true;
		final Transform transform = getNodeAnimationTransform(nodeAnim, time);
		transform.toMatrix4(node.localTransform);
	}

	private final static void applyNodeAnimationBlending (final NodeAnimation nodeAnim, final ObjectMap<Node, Transform> out,
		final Pool<Transform> pool, final float alpha, final float time) {

		final Node node = nodeAnim.node;
		node.isAnimated = true;
		final Transform transform = getNodeAnimationTransform(nodeAnim, time);

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

	/** Helper method to apply one animation to either an objectmap for blending or directly to the bones. */
	protected static void applyAnimation (final ObjectMap<Node, Transform> out, final Pool<Transform> pool, final float alpha,
		final Animation animation, final float time) {

		if (out == null) {
			for (final NodeAnimation nodeAnim : animation.nodeAnimations)
				applyNodeAnimationDirectly(nodeAnim, time);
		} else {
			for (final Node node : out.keys())
				node.isAnimated = false;
			for (final NodeAnimation nodeAnim : animation.nodeAnimations)
				applyNodeAnimationBlending(nodeAnim, out, pool, alpha, time);
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
		for (final NodeAnimation nodeAnim : animation.nodeAnimations) {
			nodeAnim.node.isAnimated = false;
		}
	}
}

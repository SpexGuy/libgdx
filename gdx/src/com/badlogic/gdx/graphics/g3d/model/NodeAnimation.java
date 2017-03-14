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

package com.badlogic.gdx.graphics.g3d.model;

import com.badlogic.gdx.graphics.g3d.Model;

/** A NodeAnimation defines keyframes for a {@link Node} in a {@link Model}. The keyframes are given as a translation vector, a
 * rotation quaternion and a scale vector. Keyframes are interpolated linearly for now. Keytimes are given in seconds.
 * @author badlogic, Xoppa */
public class NodeAnimation {
	/** the Node affected by this animation **/
	public Node node;
	/** the offset in entries of the translation frames in times, or -1 if translation is not specified. Data stride is 4 floats. **/
	public int translationOffset = -1;
	public int translationCount = 0;
	/** the offset in entries of the rotation frames in times, or -1 if rotation is not specified. Data stride is 4 floats. **/
	public int rotationOffset = -1;
	public int rotationCount = 0;
	/** the offset in entries of the scale frames in times, or -1 if scale is not specified. Data stride is 4 floats. **/
	public int scaleOffset = -1;
	public int scaleCount = 0;
	/** the keyframe times, sorted by time ascending. **/
	public float[] times;
	/** the keyframe data, sorted by time ascending **/
	public float[] data;
}

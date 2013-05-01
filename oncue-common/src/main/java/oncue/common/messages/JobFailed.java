/*******************************************************************************
 * Copyright 2013 Michael Marconi
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
package oncue.common.messages;

import java.io.Serializable;

public class JobFailed implements Serializable {

	private static final long serialVersionUID = 8581566254214368256L;
	private final Job job;
	private final Throwable error;

	public JobFailed(Job job, Throwable error) {
		this.job = (Job) job.clone();
		this.error = error;
	}

	public Throwable getError() {
		return error;
	}

	public Job getJob() {
		return job;
	}

}

/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 */
package org.thingml.compilers.javascript;

import java.util.LinkedList;
import java.util.List;

import org.thingml.compilers.Context;
import org.thingml.compilers.builder.Section;
import org.thingml.compilers.builder.SourceBuilder;
import org.thingml.compilers.configuration.CfgBuildCompiler;
import org.thingml.compilers.configuration.CfgMainGenerator;
import org.thingml.compilers.thing.ThingActionCompiler;
import org.thingml.compilers.thing.ThingApiCompiler;
import org.thingml.compilers.thing.common.NewFSMBasedThingImplCompiler;
import org.thingml.compilers.utils.OpaqueThingMLCompiler;
import org.thingml.utilities.logging.Logger;
import org.thingml.xtext.constraints.ThingMLHelpers;
import org.thingml.xtext.helpers.AnnotatedElementHelper;
import org.thingml.xtext.helpers.ConfigurationHelper;
import org.thingml.xtext.thingML.Configuration;
import org.thingml.xtext.thingML.Enumeration;
import org.thingml.xtext.thingML.EnumerationLiteral;
import org.thingml.xtext.thingML.Thing;
import org.thingml.xtext.thingML.ThingMLModel;
import org.thingml.xtext.thingML.Type;
import org.thingml.xtext.validation.Checker;

public abstract class JavascriptCompiler extends OpaqueThingMLCompiler {

	public JavascriptCompiler(ThingActionCompiler thingActionCompiler, ThingApiCompiler thingApiCompiler,
			CfgMainGenerator mainCompiler, CfgBuildCompiler cfgBuildCompiler, NewFSMBasedThingImplCompiler thingImplCompiler) {
		super(thingActionCompiler, thingApiCompiler, mainCompiler, cfgBuildCompiler, thingImplCompiler);
		this.checker = new Checker(getID(), null);
		this.ctx = new JSContext(this);
	}

	@Override
    public void do_call_compiler(Configuration cfg, Logger log, String... options) {
        this.checker.do_check(cfg, false);
        //this.checker.printReport(log);

        ctx.addContextAnnotation("thisRef", "this.");
        //new File(ctx.getOutputDirectory() + "/" + cfg.getName()).mkdirs();
        ctx.setCurrentConfiguration(cfg);
        compile(cfg, ThingMLHelpers.findContainingModel(cfg), true, ctx);
        ctx.getCompiler().getCfgBuildCompiler().generateDockerFile(cfg, ctx);
        ctx.getCompiler().getCfgBuildCompiler().generateBuildScript(cfg, ctx);
        ctx.writeGeneratedCodeToFiles();
        ctx.generateNetworkLibs(cfg);
    }
	
	abstract protected String getEnumPath(Configuration t, ThingMLModel model, Context ctx);
	protected void generateEnums(Configuration t, ThingMLModel model, Context ctx) {
		List<Enumeration> enums = new LinkedList<Enumeration>();
		
		for (Type ty : ThingMLHelpers.allTypes/*allUsedSimpleTypes*/(model))
            if (ty instanceof Enumeration)
            	enums.add((Enumeration)ty);
		
		if (!enums.isEmpty()) {
			ctx.addContextAnnotation("hasEnum", "true");
			SourceBuilder builder = ctx.getSourceBuilder(getEnumPath(t, model, ctx));
			
			for (Enumeration e : enums) {
				Section sec = builder.section("enumeration").lines();
				sec.comment("Definition of Enumeration "+e.getName());
				sec.append("const "+e.getName()+"_ENUM = Object.freeze({");
				Section literals = sec.section("literals").lines().indent();
				for (EnumerationLiteral l : e.getLiterals()) {
					Section literal = literals.section("literal");
					literal.append(l.getName().toUpperCase()).append(": ");
					
					String val = AnnotatedElementHelper.annotationOrElse(l, "enum_val", l.getName());
					try {
						literal.append(Integer.parseInt(val));
					} catch (NumberFormatException ex) {
						literal.append('"').append(val).append('"');
					}
					literal.append(",");
				}
				sec.append("});");
				sec.append("exports."+e.getName()+"_ENUM = "+e.getName()+"_ENUM;");
				sec.append("");
			}
		}
	}

    private void compile(Configuration t, ThingMLModel model, boolean isNode, Context ctx) {
        processDebug(t); // TODO: What does this actually do??
        generateEnums(t, model, ctx);
        for (Thing thing : ConfigurationHelper.allThings(t)) {
            ctx.getCompiler().getThingImplCompiler().generateImplementation(thing, ctx);
        }
        ctx.getCompiler().getMainCompiler().generateMainAndInit(t, model, ctx);
    }
}